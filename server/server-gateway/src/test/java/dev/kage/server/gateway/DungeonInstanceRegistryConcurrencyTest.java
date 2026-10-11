package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterUseCases;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.application.SelectFactionUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.Transaction;
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
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

/**
 * Hai thành viên cùng nhóm bấm "vào hầm ngục" ĐÚNG cùng lúc. {@code authExecutor} là
 * một-Virtual-Thread-mỗi-task nên hai lượt {@link DungeonInstanceRegistry#enterDungeon} chạy song
 * song thật. Trước bản vá 2026-09-24 bảng định tuyến là {@code HashMap} trần: cả hai lượt cùng thấy
 * "nhóm chưa có instance", dựng HAI World (mỗi cái một boss) và tách nhóm ra hai nơi.
 *
 * <p>Đo GIÁ TRỊ CUỐI (hai người có chung một World không), không đo "có chặn không" — và lặp nhiều
 * vòng để cửa sổ race có cơ hội mở. Rào {@link CyclicBarrier} đặt trong {@code listMembers}: lời
 * gọi DB cuối cùng trước phần ghi, nên hai luồng lao vào phần ghi cùng một lúc.
 */
@Timeout(60)
class DungeonInstanceRegistryConcurrencyTest {

    private static final int ROUNDS = 20;
    private static final long PARTY_ID = 1L;
    static final long LEADER_ID = 101L;
    static final long MEMBER_ID = 102L;

    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile CyclicBarrier barrier = new CyclicBarrier(1);

    @BeforeEach
    void setUp() {
        // Inventory/quest KHÔNG nằm trên đường vào hầm ngục (chỉ rời hầm ngục mới hỏi trang bị) —
        // null thay vì dựng hai facade cồng kềnh chỉ để không bao giờ gọi tới.
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                null,
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        characterRegistry.register(
                LEADER_ID,
                new EmbeddedChannel(new ChannelInboundHandlerAdapter())
                        .pipeline()
                        .firstContext());
        characterRegistry.register(
                MEMBER_ID,
                new EmbeddedChannel(new ChannelInboundHandlerAdapter())
                        .pipeline()
                        .firstContext());
        WorldFacade firstChannel = new WorldFacade(
                characterRegistry, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry,
                firstChannel,
                new SimpleMeterRegistry(),
                TestWorldContents.trainingGroundWithElder());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void shouldPutTheWholePartyIntoOneInstance_whenTwoMembersEnterAtTheSameInstant() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            DungeonInstanceRegistry dungeons = new DungeonInstanceRegistry(
                    characterRegistry,
                    new DungeonInstanceRegistry.DungeonFacades(
                            fakeCharacterFacade(), fakePartyFacade(this::awaitBarrier)),
                    mainWorldChannels,
                    TestWorldContents.withBoss());
            try {
                barrier = new CyclicBarrier(2);
                Future<DungeonInstanceRegistry.EnterOutcome> leader =
                        executor.submit(() -> dungeons.enterDungeon(LEADER_ID, "sample_dungeon"));
                Future<DungeonInstanceRegistry.EnterOutcome> member =
                        executor.submit(() -> dungeons.enterDungeon(MEMBER_ID, "sample_dungeon"));

                assertThat(leader.get(30, TimeUnit.SECONDS)).isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);
                assertThat(member.get(30, TimeUnit.SECONDS)).isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);
                Optional<WorldFacade> leaderWorld = dungeons.worldFacadeFor(LEADER_ID);
                assertThat(leaderWorld).as("round %d", round).isPresent();
                assertThat(dungeons.worldFacadeFor(MEMBER_ID))
                        .as("round %d", round)
                        .containsSame(leaderWorld.get());
            } finally {
                dungeons.stopAll();
            }
        }
    }

    /**
     * Đồng đội rớt mạng ĐÚNG lúc lượt vào đang đọc DB — sau lượt kiểm "cả nhóm online", trước phần
     * ghi. {@code channelInactive} chạy trọn trước (gỡ đăng ký, hầm ngục chưa biết họ, gỡ khỏi world
     * chính), rồi lượt vào mới ghi. Bản cũ không kiểm lại "còn online không" trong khoá: kéo luôn
     * người đã thoát vào hầm ngục — thực thể bóng ma không ai dọn (không còn kết nối nào sẽ chết lần
     * nữa), giữ World hầm ngục tick mãi kể cả khi cả nhóm đã ra, và lần đăng nhập sau của người đó bị
     * định tuyến nhầm vào hầm ngục.
     */
    @Test
    void shouldNotPullAMemberIntoTheDungeon_whenTheyDisconnectWhileTheEntryReadsTheDatabase() {
        DungeonInstanceRegistry[] holder = new DungeonInstanceRegistry[1];
        DungeonInstanceRegistry dungeons = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        fakeCharacterFacade(characterId -> {
                            if (characterId == MEMBER_ID) {
                                simulateDisconnect(holder[0], MEMBER_ID);
                            }
                        }),
                        fakePartyFacade(() -> {})),
                mainWorldChannels,
                TestWorldContents.withBoss());
        holder[0] = dungeons;
        try {
            assertThat(dungeons.enterDungeon(LEADER_ID, "sample_dungeon"))
                    .isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);

            assertThat(dungeons.worldFacadeFor(LEADER_ID)).isPresent();
            assertThat(dungeons.worldFacadeFor(MEMBER_ID)).isEmpty();

            // Người còn lại ra ⇒ không còn ai, World hầm ngục phải dừng — bóng ma không được giữ nó.
            assertThat(dungeons.exitDungeon(LEADER_ID)).isEqualTo(DungeonInstanceRegistry.ExitOutcome.OK);
            assertThat(dungeons.instanceCount()).isZero();
        } finally {
            dungeons.stopAll();
        }
    }

    /**
     * Cả nhóm rớt mạng trong lúc lượt vào đọc DB ⇒ không ai được nhận. Bản cũ dựng instance TRƯỚC khi
     * biết có nhận được ai, nên một instance rỗng (kèm boss) nằm lại trong bảng, tick mãi.
     */
    @Test
    void shouldNotLeaveAnEmptyInstanceRunning_whenTheWholePartyDisconnectsDuringEntry() {
        DungeonInstanceRegistry[] holder = new DungeonInstanceRegistry[1];
        DungeonInstanceRegistry dungeons = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        fakeCharacterFacade(characterId -> simulateDisconnect(holder[0], characterId)),
                        fakePartyFacade(() -> {})),
                mainWorldChannels,
                TestWorldContents.withBoss());
        holder[0] = dungeons;
        try {
            dungeons.enterDungeon(LEADER_ID, "sample_dungeon");

            assertThat(dungeons.worldFacadeFor(LEADER_ID)).isEmpty();
            assertThat(dungeons.worldFacadeFor(MEMBER_ID)).isEmpty();
            assertThat(dungeons.instanceCount()).isZero();
        } finally {
            dungeons.stopAll();
        }
    }

    /** Đúng thứ tự của {@code GatewayChannelHandler.releaseCharacter}: gỡ đăng ký → hầm ngục → world chính. */
    private void simulateDisconnect(DungeonInstanceRegistry dungeons, long characterId) {
        if (!characterRegistry.isOnline(characterId)) {
            return;
        }
        characterRegistry.unregister(characterId);
        if (!dungeons.handleDisconnectIfInDungeon(characterId)) {
            mainWorldChannels.detachCharacter(characterId);
        }
    }

    static CharacterFacade fakeCharacterFacade() {
        return fakeCharacterFacade(characterId -> {});
    }

    /**
     * @param onLoadStats chạy mỗi lần đọc chỉ số — lời gọi DB đầu tiên của {@code CharacterLoadout},
     *     tức ĐÚNG khoảng hở giữa lượt kiểm "cả nhóm online" và phần ghi của lượt vào hầm ngục
     */
    static CharacterFacade fakeCharacterFacade(java.util.function.LongConsumer onLoadStats) {
        return fakeCharacterFacade(onLoadStats, () -> 1);
    }

    /** @param storedLevel cấp "trong DB" lúc được đọc — cho test dựng một cú lên cấp giữa hai lần đọc */
    static CharacterFacade fakeCharacterFacade(
            java.util.function.LongConsumer onLoadStats, java.util.function.IntSupplier storedLevel) {
        CharacterPort port = new CharacterPort() {
            @Override
            public Optional<Character> findById(long characterId) {
                return Optional.of(new Character(
                        characterId,
                        1L,
                        1,
                        "Hero" + characterId,
                        Faction.HOA,
                        storedLevel.getAsInt(),
                        0L,
                        0,
                        Instant.EPOCH));
            }

            @Override
            public CharacterStats loadStats(long characterId) {
                onLoadStats.accept(characterId);
                return new CharacterStats(10, 10, 10, 10);
            }

            @Override
            public PositionSnapshot loadPosition(long characterId) {
                return new PositionSnapshot(0f, 0f);
            }

            @Override
            public List<Character> listByAccount(long accountId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public int countByAccount(long accountId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public int lockAccountAndCountCharacters(dev.kage.character.domain.Transaction tx, long accountId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public boolean existsByName(String name) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public Optional<Character> findByName(String name) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public Character create(Transaction tx, NewCharacter newCharacter) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public void savePosition(long characterId, float x, float y) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public long loadGold(long characterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public long addExperience(long characterId, long amount) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public boolean saveLevel(long characterId, int level, long derivedFromExp) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public void updateFaction(long characterId, Faction faction) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }
        };
        CharacterUseCases useCases = new CharacterUseCases(
                new CreateCharacterUseCase(port, action -> action.accept(null)),
                new ListCharactersUseCase(port),
                new SelectCharacterUseCase(port),
                new SelectFactionUseCase(port),
                new GrantExperienceUseCase(port));
        return new CharacterFacade(useCases, port);
    }

    /**
     * Nhóm hai người {@link #LEADER_ID} + {@link #MEMBER_ID}.
     *
     * @param beforeListMembers chạy ngay trước khi trả danh sách thành viên — chỗ đặt rào cho test
     *     race; test khác truyền việc rỗng
     */
    static PartyFacade fakePartyFacade(Runnable beforeListMembers) {
        PartyPort port = new PartyPort() {
            @Override
            public Optional<PartyMember> findMembershipOf(long characterId) {
                PartyRole role = characterId == LEADER_ID ? PartyRole.LEADER : PartyRole.MEMBER;
                return Optional.of(new PartyMember(PARTY_ID, characterId, role, Instant.EPOCH));
            }

            @Override
            public List<PartyMember> listMembers(long partyId) {
                beforeListMembers.run();
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
            public boolean transferLeadership(long leaderCharacterId, long newLeaderCharacterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public int deleteInvitesInvitedBefore(java.time.Instant cutoff) {
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
                new FindMyPartyUseCase(port),
                new dev.kage.party.application.HandOverPartyLeadershipUseCase(port)));
    }

    private void awaitBarrier() {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi chờ rào", e);
        } catch (java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("rào hỏng", e);
        }
    }
}
