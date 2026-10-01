package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.party.api.PartyFacade;
import dev.kage.party.application.AcceptPartyInviteUseCase;
import dev.kage.party.application.CreatePartyUseCase;
import dev.kage.party.application.FindMyPartyUseCase;
import dev.kage.party.application.InvitePartyMemberUseCase;
import dev.kage.party.application.KickPartyMemberUseCase;
import dev.kage.party.application.LeavePartyUseCase;
import dev.kage.party.application.ListPartyMembersUseCase;
import dev.kage.party.application.ListPendingPartyInvitesUseCase;
import dev.kage.party.application.PartyUseCases;
import dev.kage.party.application.RejectPartyInviteUseCase;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

/**
 * ADR-051 — {@code WorldChangedEvent} tới TRƯỚC gói spawn đầu tiên của World mới, ở MỌI cú đổi
 * World (D2), và gục trong hầm ngục đưa người chơi về world chính (D5).
 *
 * <p>Ghi lại gói ra bằng một handler outbound đặt trước context đăng ký: gói từ tick thread của
 * World và từ luồng test đều đi qua nó (EmbeddedEventLoop coi mọi luồng là "trong event loop"), và
 * nó không chạm hàng đợi outbound của EmbeddedChannel (vốn không an toàn đa luồng).
 */
@Timeout(60)
class WorldChangedEventOrderTest {

    private static final long LEADER_ID = 301L;
    private static final long MEMBER_ID = 302L;
    private static final long PARTY_ID = 7L;
    private static final Duration WAIT = Duration.ofSeconds(10);

    /** Gói spawn của World hầm ngục/đấu trường: nhân vật khác và quái (boss). */
    private static final List<Integer> NEW_WORLD_SPAWNS =
            List.of(Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE);

    private final Map<Long, ConcurrentLinkedQueue<PacketCodec.Frame>> sent = new ConcurrentHashMap<>();
    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private DungeonInstanceRegistry dungeons;
    private ArenaInstanceRegistry arena;

    @BeforeEach
    void setUp() {
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                null,
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        for (long id : new long[] {LEADER_ID, MEMBER_ID}) {
            ConcurrentLinkedQueue<PacketCodec.Frame> frames = new ConcurrentLinkedQueue<>();
            sent.put(id, frames);
            EmbeddedChannel channel = new EmbeddedChannel(new Recorder(frames), new ChannelInboundHandlerAdapter());
            characterRegistry.register(id, channel.pipeline().lastContext());
        }
        WorldFacade firstChannel = new WorldFacade(
                characterRegistry, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry,
                firstChannel,
                new SimpleMeterRegistry(),
                TestWorldContents.trainingGroundWithElder());
        dungeons = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(), twoMemberParty()),
                mainWorldChannels,
                TestWorldContents.withBoss());
        arena = new ArenaInstanceRegistry(
                characterRegistry,
                DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(),
                new PvpFacade(new FixedRatingPort()),
                mainWorldChannels);
    }

    @AfterEach
    void tearDown() {
        dungeons.stopAll();
        arena.stopAll();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void requesterAndPulledMemberShouldBeToldBeforeTheFirstSpawnOfTheDungeon() throws Exception {
        assertThat(dungeons.enterDungeon(LEADER_ID, "sample_dungeon"))
                .isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);

        awaitOpcode(LEADER_ID, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE);
        awaitOpcode(MEMBER_ID, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE);

        assertWorldChangedBeforeFirstSpawn(
                LEADER_ID, WorldKind.WORLD_KIND_DUNGEON, WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED);
        assertWorldChangedBeforeFirstSpawn(
                MEMBER_ID, WorldKind.WORLD_KIND_DUNGEON, WorldChangeReason.WORLD_CHANGE_REASON_PULLED_BY_PARTY);
        // D4 — 1504 vẫn phát cho người bị kéo (client cũ).
        assertThat(opcodes(MEMBER_ID)).contains(Opcode.OPCODE_DUNGEON_ENTERED_EVENT_VALUE);
        assertThat(opcodes(LEADER_ID)).doesNotContain(Opcode.OPCODE_DUNGEON_ENTERED_EVENT_VALUE);
    }

    /**
     * D5 — trước ADR-051 lời báo gục của World hầm ngục rơi vào một hàm rỗng, và nhân vật nằm 0 máu
     * trong hầm ngục tới khi tự bấm rời. {@code handleCharacterDefeated} chính là đích mà
     * {@link DungeonOutboundSink#onCharacterDefeated} gọi tới (xem test ngay dưới).
     */
    @Test
    void defeatInsideTheDungeonShouldBringThePlayerBackToTheMainWorld() throws Exception {
        dungeons.enterDungeon(LEADER_ID, "sample_dungeon");
        WorldFacade firstInstance = dungeons.worldFacadeFor(LEADER_ID).orElseThrow();
        clearRecorded();

        dungeons.handleCharacterDefeated(LEADER_ID);

        awaitTrue(() -> dungeons.worldFacadeFor(LEADER_ID).isEmpty());
        assertThat(mainWorldChannels.worldFacadeFor(LEADER_ID)).isPresent();
        assertThat(dungeons.worldFacadeFor(MEMBER_ID))
                .as("đồng đội còn sống vẫn ở trong")
                .containsSame(firstInstance);
        WorldChangedEvent back = onlyWorldChanged(LEADER_ID);
        assertThat(back.getWorldKind()).isEqualTo(WorldKind.WORLD_KIND_MAIN);
        assertThat(back.getReason()).isEqualTo(WorldChangeReason.WORLD_CHANGE_REASON_DEFEATED);
        assertThat(back.getCurrentHp()).as("về world chính sống, đầy máu").isEqualTo(back.getMaxHp());
        assertThat(back.getMaxHp()).isPositive();
        awaitOpcode(LEADER_ID, Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE);
        assertThat(indexOf(LEADER_ID, Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE))
                .as("event đổi World phải tới trước spawn đầu tiên của world chính")
                .isLessThan(indexOf(LEADER_ID, Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE));

        // Người cuối gục ⇒ instance dừng và bị gỡ: lần vào sau dựng World MỚI.
        dungeons.handleCharacterDefeated(MEMBER_ID);
        awaitTrue(() -> dungeons.worldFacadeFor(MEMBER_ID).isEmpty());
        dungeons.enterDungeon(LEADER_ID, "sample_dungeon");
        assertThat(dungeons.worldFacadeFor(LEADER_ID).orElseThrow()).isNotSameAs(firstInstance);
    }

    @Test
    void dungeonSinkShouldForwardDefeatToTheRegistry() {
        ConcurrentLinkedQueue<Long> defeated = new ConcurrentLinkedQueue<>();
        DungeonOutboundSink sink = new DungeonOutboundSink(characterRegistry, defeated::add);

        sink.onCharacterDefeated(LEADER_ID, -1L);

        assertThat(defeated).containsExactly(LEADER_ID);
    }

    @Test
    void bothDuelistsShouldBeToldBeforeTheArenaSpawnsAndAgainBeforeTheMainWorldSpawns() throws Exception {
        long challengeId = arena.challengeDuel(LEADER_ID, MEMBER_ID).challengeId();
        assertThat(arena.acceptDuel(MEMBER_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);

        awaitOpcode(LEADER_ID, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE);
        awaitOpcode(MEMBER_ID, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE);
        assertWorldChangedBeforeFirstSpawn(
                LEADER_ID, WorldKind.WORLD_KIND_ARENA, WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED);
        assertWorldChangedBeforeFirstSpawn(
                MEMBER_ID, WorldKind.WORLD_KIND_ARENA, WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED);
        clearRecorded();

        assertThat(arena.forfeitDuel(LEADER_ID).outcome()).isEqualTo(ArenaInstanceRegistry.ExitOutcome.OK);

        for (long id : new long[] {LEADER_ID, MEMBER_ID}) {
            awaitOpcode(id, Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE);
            WorldChangedEvent back = onlyWorldChanged(id);
            assertThat(back.getWorldKind()).isEqualTo(WorldKind.WORLD_KIND_MAIN);
            assertThat(back.getReason()).isEqualTo(WorldChangeReason.WORLD_CHANGE_REASON_DUEL_ENDED);
            assertThat(indexOf(id, Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE))
                    .isLessThan(indexOf(id, Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE));
        }
    }

    /**
     * Quyết định chủ dự án 2026-10-01 — kiểm sống 2026-09-25 đo được {@code 304} (đối thủ rời world
     * chính) tới SAU {@code 308 ARENA} ở 2/3 lượt; entity id = characterId ở mọi World nên client xoá
     * mất đối thủ khỏi màn đấu trường. Đua theo tick ⇒ lặp nhiều trận để bản cũ lộ ra.
     */
    @Test
    void mainWorldShouldFallSilentBeforeTheArenaIsAnnounced() throws Exception {
        for (int round = 0; round < 20; round++) {
            clearRecorded();
            long challengeId = arena.challengeDuel(LEADER_ID, MEMBER_ID).challengeId();
            assertThat(arena.acceptDuel(MEMBER_ID, challengeId).outcome())
                    .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);
            for (long id : new long[] {LEADER_ID, MEMBER_ID}) {
                // Spawn của World đấu trường = spawn SAU 308 (spawn trước đó có thể là gói muộn của lượt
                // trước, khi cả hai vừa về world chính).
                awaitTrue(() -> {
                    List<Integer> now = opcodes(id);
                    int announced = now.indexOf(Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE);
                    return announced >= 0
                            && now.subList(announced, now.size()).contains(Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE);
                });
                // Đợi thêm vài tick: gói muộn của world chính (nếu còn) có thời gian rơi vào.
                Thread.sleep(50);
                List<Integer> received = opcodes(id);
                assertThat(received.lastIndexOf(Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE))
                        .as("lượt %d, characterId=%d nhận %s", round, id, received)
                        .isLessThan(received.indexOf(Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE));
            }
            assertThat(arena.forfeitDuel(LEADER_ID).outcome()).isEqualTo(ArenaInstanceRegistry.ExitOutcome.OK);
            awaitTrue(() -> mainWorldChannels.worldFacadeFor(LEADER_ID).isPresent()
                    && mainWorldChannels.worldFacadeFor(MEMBER_ID).isPresent());
        }
    }

    private void assertWorldChangedBeforeFirstSpawn(long characterId, WorldKind kind, WorldChangeReason reason)
            throws InvalidProtocolBufferException {
        WorldChangedEvent event = onlyWorldChanged(characterId);
        assertThat(event.getWorldKind()).isEqualTo(kind);
        assertThat(event.getReason()).isEqualTo(reason);
        int firstSpawn = Integer.MAX_VALUE;
        for (int opcode : NEW_WORLD_SPAWNS) {
            int index = indexOf(characterId, opcode);
            if (index >= 0) {
                firstSpawn = Math.min(firstSpawn, index);
            }
        }
        assertThat(indexOf(characterId, Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE))
                .as("characterId=%d nhận %s", characterId, opcodes(characterId))
                .isLessThan(firstSpawn);
    }

    private WorldChangedEvent onlyWorldChanged(long characterId) throws InvalidProtocolBufferException {
        List<PacketCodec.Frame> events = sent.get(characterId).stream()
                .filter(f -> f.header().opcode() == Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE)
                .toList();
        assertThat(events)
                .as("characterId=%d nhận %s", characterId, opcodes(characterId))
                .hasSize(1);
        return WorldChangedEvent.parseFrom(events.get(0).payload());
    }

    private List<Integer> opcodes(long characterId) {
        return sent.get(characterId).stream().map(f -> f.header().opcode()).toList();
    }

    private int indexOf(long characterId, int opcode) {
        return opcodes(characterId).indexOf(opcode);
    }

    private void clearRecorded() {
        sent.values().forEach(ConcurrentLinkedQueue::clear);
    }

    private void awaitOpcode(long characterId, int opcode) throws InterruptedException {
        awaitTrue(() -> opcodes(characterId).contains(opcode));
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("hết giờ chờ").isBefore(deadline);
            Thread.sleep(20);
        }
    }

    /** Ghi lại mọi gói ra rồi coi như đã gửi — không đẩy tiếp xuống hàng đợi của EmbeddedChannel. */
    private static final class Recorder extends ChannelOutboundHandlerAdapter {

        private final ConcurrentLinkedQueue<PacketCodec.Frame> frames;

        private Recorder(ConcurrentLinkedQueue<PacketCodec.Frame> frames) {
            this.frames = frames;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof PacketCodec.Frame frame) {
                frames.add(frame);
            }
            promise.setSuccess();
        }
    }

    /** Rating cố định — chốt trận chỉ cần một con số trả về, không phải test Elo. */
    private static final class FixedRatingPort implements PvpRatingPort {

        @Override
        public Optional<PvpRating> findByCharacterId(long characterId) {
            return Optional.empty();
        }

        @Override
        public EloRating.Result recordDuelResult(
                long winnerCharacterId, long loserCharacterId, PvpRatingPort.EloComputer computer) {
            return new EloRating.Result(1016, 984);
        }
    }

    private static PartyFacade twoMemberParty() {
        PartyPort port = new PartyPort() {
            @Override
            public Optional<PartyMember> findMembershipOf(long characterId) {
                PartyRole role = characterId == LEADER_ID ? PartyRole.LEADER : PartyRole.MEMBER;
                return Optional.of(new PartyMember(PARTY_ID, characterId, role, Instant.EPOCH));
            }

            @Override
            public List<PartyMember> listMembers(long partyId) {
                return List.of(
                        new PartyMember(PARTY_ID, LEADER_ID, PartyRole.LEADER, Instant.EPOCH),
                        new PartyMember(PARTY_ID, MEMBER_ID, PartyRole.MEMBER, Instant.EPOCH));
            }

            @Override
            public Party createParty(long leaderCharacterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public Optional<PartyInvite> findPendingInviteOfInvitee(long characterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public List<PartyInvite> listPendingInvitesOfInvitee(long characterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public PartyInvite invite(long partyId, long inviteeCharacterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public AcceptResult acceptInvite(long inviteId, long characterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public boolean rejectInvite(long inviteId, long characterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public LeaveResult leaveParty(long characterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }
        };
        return new PartyFacade(new PartyUseCases(
                new CreatePartyUseCase(port),
                new InvitePartyMemberUseCase(port),
                new AcceptPartyInviteUseCase(port),
                new RejectPartyInviteUseCase(port),
                new LeavePartyUseCase(port),
                new KickPartyMemberUseCase(port),
                new ListPartyMembersUseCase(port),
                new ListPendingPartyInvitesUseCase(port),
                new FindMyPartyUseCase(port)));
    }
}
