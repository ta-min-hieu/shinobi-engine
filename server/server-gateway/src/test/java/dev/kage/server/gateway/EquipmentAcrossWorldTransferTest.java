package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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

import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.Opcode;
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
 * Vào hầm ngục / đấu trường phải mang THEO chỉ số trang bị — cả trong thực thể của World mới lẫn
 * máu ghi trong {@code WorldChangedEvent} (ADR-051).
 *
 * <p>Bug ghi nhận 2026-09-24: hai đường vào attach bằng chỉ số NỀN (overload 4 tham số của
 * {@code WorldFacade#attachCharacter}), nên người mặc giáp +máu vào hầm ngục với 100 máu, và event
 * đổi World cũng báo 100. Đường VỀ world chính thì đã cộng bonus từ 2026-09-18 — chỉ đường VÀO sót.
 *
 * <p>Máu của THỰC THỂ (không chỉ của event) đọc qua đòn đánh đầu tiên trúng nhân vật:
 * {@code damage + target_current_hp} = máu ngay trước đòn = máu tối đa (vừa vào, máu đầy). Bonus máu
 * đặt rất lớn để một đòn không thể tiêu hết phần chênh.
 */
@Timeout(60)
class EquipmentAcrossWorldTransferTest {

    private static final long LEADER_ID = DungeonInstanceRegistryConcurrencyTest.LEADER_ID;
    private static final long MEMBER_ID = DungeonInstanceRegistryConcurrencyTest.MEMBER_ID;

    private static final long SWORD_TEMPLATE_ID = 2L;
    private static final long SWORD_ITEM_ID = 42L;
    private static final int BONUS_ATK = 7;
    private static final int BONUS_HP = 1000;
    private static final int EFFECTIVE_MAX_HP =
            WorldFacade.characterMaxHp(new dev.kage.server.world.EquipmentBonusSnapshot(BONUS_ATK, 0, 0, BONUS_HP));

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final long POLL_MILLIS = 20L;

    /** Lớn hơn hồi chiêu đánh thường 1 giây — xem bẫy "nhịp đánh phải > cooldown". */
    private static final long ATTACK_INTERVAL_MILLIS = 1100L;

    private final Map<Long, ConcurrentLinkedQueue<PacketCodec.Frame>> sent = new ConcurrentHashMap<>();
    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private DungeonInstanceRegistry dungeons;
    private ArenaInstanceRegistry arena;

    @BeforeEach
    void setUp() {
        ItemTemplate sword =
                new ItemTemplate(SWORD_TEMPLATE_ID, EquipmentSlotType.WEAPON, 1, BONUS_ATK, 0, 0, BONUS_HP, 1, null, 0);
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(
                        new GatewayPipelineTest.SeededInventory(
                                List.of(new ItemStack(SWORD_ITEM_ID, SWORD_TEMPLATE_ID, 1, 0)),
                                Map.of(EquipmentSlotType.WEAPON, SWORD_ITEM_ID),
                                Map.of(SWORD_TEMPLATE_ID, sword)),
                        List.of()),
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
                characterRegistry, false, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry,
                firstChannel,
                new SimpleMeterRegistry(),
                TestWorldContents.trainingGroundWithElder());
        dungeons = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(),
                        DungeonInstanceRegistryConcurrencyTest.fakePartyFacade(() -> {})),
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
    void enteringTheDungeonShouldKeepEquipmentBonus_inTheEventAndInTheEntity() throws Exception {
        assertThat(dungeons.enterDungeon(LEADER_ID, "sample_dungeon"))
                .isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);

        // Người bấm (REQUESTED) và người bị kéo theo (PULLED_BY_PARTY) — hai lời gọi attach khác nhau.
        for (long id : new long[] {LEADER_ID, MEMBER_ID}) {
            WorldChangedEvent entered = onlyWorldChanged(id);
            assertThat(entered.getWorldKind()).isEqualTo(WorldKind.WORLD_KIND_DUNGEON);
            assertThat(entered.getMaxHp()).as("máu tối đa gồm bonus trang bị").isEqualTo(EFFECTIVE_MAX_HP);
            assertThat(entered.getCurrentHp()).as("vào World mới là đầy máu").isEqualTo(EFFECTIVE_MAX_HP);
        }

        // Boss đứng cạnh điểm xuất hiện và tự đánh ai đó trong nhóm — không cần ra lệnh gì.
        AttackResultEvent firstHit = awaitFirstHitOn(() -> {}, LEADER_ID, MEMBER_ID);
        assertThat(firstHit.getDamage() + firstHit.getTargetCurrentHp())
                .as("máu của THỰC THỂ trong hầm ngục trước đòn đầu tiên")
                .isEqualTo(EFFECTIVE_MAX_HP);
    }

    @Test
    void acceptingADuelShouldKeepEquipmentBonus_inTheEventAndInTheEntity() throws Exception {
        long challengeId = arena.challengeDuel(LEADER_ID, MEMBER_ID).challengeId();
        assertThat(arena.acceptDuel(MEMBER_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);

        for (long id : new long[] {LEADER_ID, MEMBER_ID}) {
            WorldChangedEvent entered = onlyWorldChanged(id);
            assertThat(entered.getWorldKind()).isEqualTo(WorldKind.WORLD_KIND_ARENA);
            assertThat(entered.getMaxHp()).as("máu tối đa gồm bonus trang bị").isEqualTo(EFFECTIVE_MAX_HP);
            assertThat(entered.getCurrentHp()).isEqualTo(EFFECTIVE_MAX_HP);
        }

        WorldFacade duel = arena.worldFacadeFor(LEADER_ID).orElseThrow();
        duel.setTarget(LEADER_ID, MEMBER_ID);
        AttackResultEvent firstHit = awaitFirstHitOn(() -> duel.attack(LEADER_ID), MEMBER_ID);
        assertThat(firstHit.getDamage() + firstHit.getTargetCurrentHp())
                .as("máu của THỰC THỂ trong đấu trường trước đòn đầu tiên")
                .isEqualTo(EFFECTIVE_MAX_HP);
    }

    /**
     * Chờ rồi mới đếm: từ 2026-10-01 (quyết định P1) đấu trường gửi 308 trên luồng riêng SAU mốc
     * "world chính đã im" — {@code acceptDuel} trả về trước khi event kịp ghi.
     */
    private WorldChangedEvent onlyWorldChanged(long characterId)
            throws InvalidProtocolBufferException, InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        List<PacketCodec.Frame> events = worldChangedFrames(characterId);
        while (events.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(POLL_MILLIS);
            events = worldChangedFrames(characterId);
        }
        assertThat(events).hasSize(1);
        return WorldChangedEvent.parseFrom(events.get(0).payload());
    }

    private List<PacketCodec.Frame> worldChangedFrames(long characterId) {
        return sent.get(characterId).stream()
                .filter(f -> f.header().opcode() == Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE)
                .toList();
    }

    /** Đòn đầu tiên trúng một trong {@code targetIds}; {@code attack} được gọi lại mỗi nhịp tới khi có đòn. */
    private AttackResultEvent awaitFirstHitOn(Runnable attack, long... targetIds)
            throws InterruptedException, InvalidProtocolBufferException {
        Instant deadline = Instant.now().plus(WAIT);
        Instant nextAttack = Instant.now();
        while (Instant.now().isBefore(deadline)) {
            if (!Instant.now().isBefore(nextAttack)) {
                attack.run();
                nextAttack = Instant.now().plusMillis(ATTACK_INTERVAL_MILLIS);
            }
            for (long targetId : targetIds) {
                Optional<AttackResultEvent> hit = firstHitOn(targetId);
                if (hit.isPresent()) {
                    return hit.get();
                }
            }
            Thread.sleep(POLL_MILLIS);
        }
        throw new AssertionError("không có đòn nào trúng " + Arrays.toString(targetIds));
    }

    private Optional<AttackResultEvent> firstHitOn(long targetId) throws InvalidProtocolBufferException {
        for (PacketCodec.Frame frame : sent.get(targetId)) {
            if (frame.header().opcode() == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
                AttackResultEvent event = AttackResultEvent.parseFrom(frame.payload());
                if (event.getTargetEntityId() == targetId) {
                    return Optional.of(event);
                }
            }
        }
        return Optional.empty();
    }

    /** Ghi lại mọi gói ra rồi coi như đã gửi — cùng khuôn {@code WorldChangedEventOrderTest}. */
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

    /** Rating cố định — test này không đụng tới Elo. */
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
}
