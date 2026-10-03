package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

/**
 * Lệnh RỜI hầm ngục tới trong lúc lượt VÀO của chính người đó còn đang chờ world chính im (bug tìm
 * được 2026-10-03). Lượt vào chia hai nửa (nhận vào → chờ world chính → gắn vào World hầm ngục), lượt
 * rời cũng vậy (gỡ khỏi World hầm ngục → chờ World đó → về world chính). Thứ tự xấu:
 *
 * <ol>
 *   <li>vào: nhận vào, ghi định tuyến;
 *   <li>rời: xếp lệnh gỡ khỏi World hầm ngục — chưa gắn nên không gỡ được gì;
 *   <li>vào: world chính đã im ⇒ GẮN vào World hầm ngục (định tuyến vẫn còn);
 *   <li>rời: về world chính, xoá định tuyến.
 * </ol>
 *
 * <p>Thực thể gắn ở bước 3 không bao giờ bị gỡ: một bóng ma đứng trong hầm ngục của nhóm (đồng đội
 * thấy, boss đánh được, gục thì kích hoạt lượt "đưa về world chính" cho một người đã ở world chính),
 * trong khi người chơi thật đã ở world chính. Hai lệnh của cùng một kết nối chạy SONG SONG
 * ({@code authExecutor} một-Virtual-Thread-mỗi-lệnh), nên client gửi "rời" ngay sau "vào" là đủ.
 *
 * <p>Thứ tự được ÉP bằng cách chặn tick của từng World ở đúng lúc: một "cổng" trên kênh của người
 * nhận giữ lần ghi kế tiếp lại (tick thread ghi đồng bộ vào {@link EmbeddedChannel}), và một tin chat
 * khu vực là thứ bắt tick đó ghi. Đo GIÁ TRỊ CUỐI qua mắt đồng đội ở trong hầm ngục: số lần thấy người
 * rời xuất hiện trừ số lần thấy họ biến mất phải bằng 0.
 */
@Timeout(60)
class DungeonEnterExitRaceTest {

    private static final long LEADER_ID = DungeonInstanceRegistryConcurrencyTest.LEADER_ID;
    private static final long MEMBER_ID = DungeonInstanceRegistryConcurrencyTest.MEMBER_ID;
    private static final String DUNGEON_CODE = "sample_dungeon";
    /** Bốn nhịp đồng bộ AoI (WorldConstants.AOI_SYNC_RATE_HZ = 20). */
    private static final long AOI_SETTLE_MILLIS = 200;

    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private DungeonInstanceRegistry dungeons;
    private final Gate leaderGate = new Gate();
    private final Gate memberGate = new Gate();
    private EmbeddedChannel memberChannel;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @BeforeEach
    void setUp() {
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                null,
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        // Cổng đứng TRƯỚC context được đăng ký — ghi từ một context chỉ đi qua các handler phía trước nó.
        EmbeddedChannel leaderChannel = new EmbeddedChannel(leaderGate, new ChannelInboundHandlerAdapter());
        characterRegistry.register(LEADER_ID, leaderChannel.pipeline().lastContext());
        memberChannel = new EmbeddedChannel(memberGate, new ChannelInboundHandlerAdapter());
        characterRegistry.register(MEMBER_ID, memberChannel.pipeline().lastContext());
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
    }

    @AfterEach
    void tearDown() {
        leaderGate.releaseAll();
        memberGate.releaseAll();
        dungeons.stopAll();
        executor.shutdownNow();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void shouldNotLeaveAGhostInTheDungeon_whenTheRequesterExitsWhileTheirEntryIsStillInFlight() throws Exception {
        // Dựng cảnh: MEMBER ở trong hầm ngục của nhóm, LEADER đã ra và đứng ở world chính.
        assertThat(dungeons.enterDungeon(MEMBER_ID, DUNGEON_CODE)).isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);
        assertThat(dungeons.exitDungeon(LEADER_ID)).isEqualTo(DungeonInstanceRegistry.ExitOutcome.OK);
        WorldFacade mainWorld = mainWorldChannels.worldFacadeFor(LEADER_ID).orElseThrow();
        WorldFacade dungeonWorld = dungeons.worldFacadeFor(MEMBER_ID).orElseThrow();
        // Gói xuất hiện/biến mất của phần dựng cảnh ra kênh xong rồi mới bỏ đi.
        Thread.sleep(AOI_SETTLE_MILLIS);
        drainMemberFrames();

        // 1. Chặn tick world chính ⇒ lượt vào của LEADER sẽ kẹt ở "chờ world chính im".
        leaderGate.arm();
        mainWorld.enqueueAreaChatMessage(LEADER_ID, "Leader", "stall");
        leaderGate.awaitStalled();

        // 2. LEADER vào lại (gia nhập instance đang có), kẹt sau khi đã ghi định tuyến.
        Future<DungeonInstanceRegistry.EnterOutcome> enter =
                executor.submit(() -> dungeons.enterDungeon(LEADER_ID, DUNGEON_CODE));
        while (dungeons.worldFacadeFor(LEADER_ID).isEmpty()) {
            Thread.onSpinWait();
        }

        // 3. Chặn tick hầm ngục ⇒ lượt rời sẽ kẹt ở "chờ World hầm ngục im", SAU khi đã xếp lệnh gỡ.
        memberGate.arm();
        dungeonWorld.enqueueAreaChatMessage(MEMBER_ID, "Member", "stall");
        memberGate.awaitStalled();
        AtomicReference<Thread> exitThread = new AtomicReference<>();
        Future<DungeonInstanceRegistry.ExitOutcome> exit = executor.submit(() -> {
            exitThread.set(Thread.currentThread());
            return dungeons.exitDungeon(LEADER_ID);
        });
        // Lượt rời đã qua nửa đầu ⇔ luồng của nó đang chờ CÓ HẠN (mốc chặn hầm ngục, trần 1 giây) —
        // chỗ chờ có hạn duy nhất trên đường rời.
        while (exitThread.get() == null || exitThread.get().getState() != Thread.State.TIMED_WAITING) {
            Thread.onSpinWait();
        }

        // 4. Thả world chính: lượt vào chạy nửa sau TRƯỚC lượt rời.
        leaderGate.release();
        enter.get(10, TimeUnit.SECONDS);
        // 5. Thả hầm ngục: lượt rời xong.
        memberGate.release();
        assertThat(exit.get(10, TimeUnit.SECONDS)).isEqualTo(DungeonInstanceRegistry.ExitOutcome.OK);

        assertThat(dungeons.worldFacadeFor(LEADER_ID)).as("định tuyến: LEADER đã rời").isEmpty();
        assertThat(mainWorldChannels.worldFacadeFor(LEADER_ID)).as("LEADER ở world chính").isPresent();
        // Mốc chặn hàng đợi hầm ngục ⇒ mọi lệnh gắn/gỡ đã xếp đều đã chạy. Gói xuất hiện/biến mất thì
        // chỉ phát ở nhịp đồng bộ AoI (20Hz, mỗi 3 tick) — chờ thêm vài nhịp cho chắc đã ra kênh.
        dungeonWorld.inboxBarrier().get(5, TimeUnit.SECONDS);
        Thread.sleep(AOI_SETTLE_MILLIS);
        dungeons.stopAll();
        assertThat(netLeaderSightingsByMember())
                .as("đồng đội trong hầm ngục không được còn thấy LEADER (bóng ma)")
                .isZero();
    }

    /** Lần xuất hiện trừ lần biến mất của LEADER trong mắt MEMBER, từ lúc dựng cảnh xong. */
    private int netLeaderSightingsByMember() throws InvalidProtocolBufferException {
        int net = 0;
        Object message;
        while ((message = memberChannel.readOutbound()) != null) {
            if (!(message instanceof PacketCodec.Frame frame)) {
                continue;
            }
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE
                    && CharacterSpawnedEvent.parseFrom(frame.payload()).getCharacterId() == LEADER_ID) {
                net++;
            } else if (opcode == Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE
                    && CharacterDespawnedEvent.parseFrom(frame.payload()).getCharacterId() == LEADER_ID) {
                net--;
            }
        }
        return net;
    }

    private void drainMemberFrames() {
        while (memberChannel.readOutbound() != null) {
            // bỏ gói của phần dựng cảnh
        }
    }

    /** Giữ lại lần ghi KẾ TIẾP sau {@link #arm} (một lần) cho tới {@link #release}. */
    private static final class Gate extends ChannelOutboundHandlerAdapter {

        private final AtomicReference<CountDownLatch> armedRelease = new AtomicReference<>();
        private volatile CountDownLatch stalled = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(0);

        void arm() {
            stalled = new CountDownLatch(1);
            release = new CountDownLatch(1);
            armedRelease.set(release);
        }

        void awaitStalled() throws InterruptedException {
            assertThat(stalled.await(5, TimeUnit.SECONDS)).as("tick phải bị giữ ở cổng").isTrue();
        }

        void release() {
            release.countDown();
        }

        void releaseAll() {
            armedRelease.set(null);
            release.countDown();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            CountDownLatch gate = armedRelease.getAndSet(null);
            if (gate != null) {
                stalled.countDown();
                if (!gate.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("cổng không được thả — xem lỗi ở luồng test");
                }
            }
            super.write(ctx, msg, promise);
        }
    }
}
