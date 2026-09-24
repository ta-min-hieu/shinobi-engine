package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.server.world.SafeZoneCatalogSnapshot;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Hầm ngục KHÔNG được mang vùng an toàn của world chính theo (ADR-050 D8: "vùng cho dungeon/arena"
 * nằm NGOÀI phạm vi).
 *
 * <p>Bug tái hiện bằng gameProbe thật 2026-09-24: {@code DungeonInstanceRegistry} dựng World hầm
 * ngục từ NGUYÊN catalog world chính (ADR-048, để tra được mã boss), và {@code WorldFacade} cài hệ
 * thống vùng an toàn từ catalog đó bất kể là world nào. Vùng {@code sect_grounds} [-15, 5] phủ đúng
 * điểm người chơi xuất hiện trong hầm ngục (x=0), nên boss đứng cách 3m không bao giờ đánh — đứng
 * 30 giây 0 đòn; bước ra x=8 thì bị đánh sau 0,4 giây.
 */
@Timeout(30)
class DungeonWorldContentTest {

    /** Phủ điểm xuất hiện trong hầm ngục (0, 0) — cùng toạ độ {@code sect_grounds} ở DB dev. */
    private static final SafeZoneCatalogSnapshot ZONE_OVER_THE_DUNGEON_SPAWN = new SafeZoneCatalogSnapshot(
            List.of(new SafeZoneCatalogSnapshot.SafeZoneDefinition("sect_grounds", -15f, -5f, 5f, 5f, true)));

    /** Boss cách 3m, aggro 6m, đuổi 3 m/s — vài giây là quá đủ để có đòn đầu tiên. */
    private static final Duration FIRST_HIT_DEADLINE = Duration.ofSeconds(10);

    private static final long POLL_MILLIS = 10L;

    private final EmbeddedChannel leaderChannel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    private final EmbeddedChannel memberChannel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private DungeonInstanceRegistry dungeons;

    @BeforeEach
    void setUp() {
        characterRegistry = new ConnectedCharacterRegistry(
                null, null, ExperienceGrantPort.disabled(), new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        characterRegistry.register(
                DungeonInstanceRegistryConcurrencyTest.LEADER_ID,
                leaderChannel.pipeline().firstContext());
        characterRegistry.register(
                DungeonInstanceRegistryConcurrencyTest.MEMBER_ID,
                memberChannel.pipeline().firstContext());
        WorldContentCatalog mainWorldContent = new WorldContentCatalog(
                TestWorldContents.withBoss().monsters(),
                TestWorldContents.townElderOnly(),
                ZONE_OVER_THE_DUNGEON_SPAWN);
        WorldFacade firstChannel = new WorldFacade(characterRegistry, true, TickMetricsPort.NO_OP, mainWorldContent);
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry, firstChannel, new SimpleMeterRegistry(), mainWorldContent);
        dungeons = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(),
                        DungeonInstanceRegistryConcurrencyTest.fakePartyFacade(() -> {})),
                mainWorldChannels,
                mainWorldContent);
    }

    @AfterEach
    void tearDown() {
        dungeons.stopAll();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void shouldLetTheBossAttack_atTheDungeonSpawnPoint_evenWhenTheMainWorldHasASafeZoneThere() {
        assertThat(dungeons.enterDungeon(DungeonInstanceRegistryConcurrencyTest.LEADER_ID, "sample_dungeon"))
                .isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);

        assertThat(awaitHitOn(
                        DungeonInstanceRegistryConcurrencyTest.LEADER_ID,
                        DungeonInstanceRegistryConcurrencyTest.MEMBER_ID))
                .as("boss phải đánh được ai đó đứng ở điểm xuất hiện trong hầm ngục")
                .isTrue();
    }

    /** {@code true} khi một trong hai người nhận đòn đánh vào CHÍNH mình trước hạn. */
    private boolean awaitHitOn(long leaderId, long memberId) {
        Instant deadline = Instant.now().plus(FIRST_HIT_DEADLINE);
        while (Instant.now().isBefore(deadline)) {
            boolean idle = true;
            for (EmbeddedChannel channel : List.of(leaderChannel, memberChannel)) {
                Object message = channel.readOutbound();
                if (message != null) {
                    idle = false;
                }
                if (message instanceof PacketCodec.Frame frame && isHitOn(frame, leaderId, memberId)) {
                    return true;
                }
            }
            if (idle) {
                sleepQuietly();
            }
        }
        return false;
    }

    private static boolean isHitOn(PacketCodec.Frame frame, long leaderId, long memberId) {
        if (frame.header().opcode() != Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            return false;
        }
        try {
            long target = AttackResultEvent.parseFrom(frame.payload()).getTargetEntityId();
            return target == leaderId || target == memberId;
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new AssertionError("AttackResultEvent hỏng trên dây", e);
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi chờ đòn đánh của boss", e);
        }
    }
}
