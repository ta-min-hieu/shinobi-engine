package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

/**
 * {@link ArenaInstanceRegistry#stopAll} không được khoá chết với tick của chính trận nó đang dừng.
 *
 * <p>Bản cũ gọi {@code WorldFacade.stop()} — chờ lần tick đang chạy dở — khi ĐANG giữ monitor của
 * registry, còn lần tick đó, nếu vừa có người gục, gọi {@code handleCharacterDefeated} và xin đúng
 * monitor ấy: hai luồng chờ nhau mãi, trình tự tắt server đứng tại đó (không chờ thưởng hạ quái,
 * không đóng DB) tới khi Docker giết tiến trình.
 *
 * <p>Test đặt hai luồng vào đúng vị trí đó một cách xác định: mốc chặn hàng đợi chạy callback của nó
 * TRÊN tick thread, bên trong lần tick.
 */
@Timeout(60)
class ArenaShutdownDeadlockTest {

    private static final long FIRST_ID = 311L;
    private static final long SECOND_ID = 312L;
    private static final long WAIT_MILLIS = 10_000;
    private static final long POLL_MILLIS = 10;
    private static final long STOP_DEADLINE_MILLIS = 5_000;
    private static final int HOLD_ATTEMPTS = 100;

    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private ArenaInstanceRegistry arena;

    @BeforeEach
    void setUp() {
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                null,
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        for (long id : new long[] {FIRST_ID, SECOND_ID}) {
            characterRegistry.register(
                    id,
                    new EmbeddedChannel(new ChannelInboundHandlerAdapter())
                            .pipeline()
                            .firstContext());
        }
        WorldFacade firstChannel = new WorldFacade(
                characterRegistry, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry,
                firstChannel,
                new SimpleMeterRegistry(),
                TestWorldContents.trainingGroundWithElder());
        arena = new ArenaInstanceRegistry(
                characterRegistry,
                DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(characterId -> {}),
                new PvpFacade(noOpRatingPort()),
                mainWorldChannels);
    }

    /** KHÔNG gọi {@code arena.stopAll()} ở đây: nếu test đỏ vì khoá chết thì lời gọi đó cũng treo theo. */
    @AfterEach
    void tearDown() {
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void stopAllShouldReturn_whenTheDuelTickReportsADefeatWhileItIsBeingStopped() throws Exception {
        long challengeId = arena.challengeDuel(FIRST_ID, SECOND_ID).challengeId();
        assertThat(arena.acceptDuel(SECOND_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);
        waitUntil(() -> arena.worldFacadeFor(FIRST_ID).isPresent());
        WorldFacade duelWorld = arena.worldFacadeFor(FIRST_ID).orElseThrow();

        CountDownLatch insideTick = new CountDownLatch(1);
        CountDownLatch stopperIsWaiting = new CountDownLatch(1);
        holdDuelTick(duelWorld, insideTick, stopperIsWaiting);
        assertThat(insideTick.await(WAIT_MILLIS, TimeUnit.MILLISECONDS))
                .as("phải giữ được một lần tick của trận đang chạy dở")
                .isTrue();

        Thread stopper = new Thread(arena::stopAll, "arena-stop-all");
        stopper.setDaemon(true);
        stopper.start();
        // stopAll đang chờ lần tick dở (đậu trên tickLock) — giờ mới cho tick báo có người gục.
        waitUntil(() -> stopper.getState() == Thread.State.WAITING);
        stopperIsWaiting.countDown();

        stopper.join(STOP_DEADLINE_MILLIS);
        assertThat(stopper.isAlive())
                .as("stopAll phải trả về — còn sống nghĩa là khoá chết với tick thread của trận")
                .isFalse();
    }

    /**
     * Chạy một đoạn TRÊN tick thread của {@code duelWorld}, bên trong lần tick: đứng chờ tới khi
     * {@code proceed} mở rồi báo có người gục — đúng lời gọi mà {@code ArenaOutboundSink} làm.
     *
     * <p>Mốc chặn có thể đã xong trước khi callback kịp gắn vào — khi đó callback chạy ngay trên luồng
     * gọi, không phải tick thread, và không giữ được gì: thử lại.
     */
    // FutureReturnValueIgnored: future của thenRun không mang gì — kết quả đọc qua latch.
    @SuppressWarnings("FutureReturnValueIgnored")
    private void holdDuelTick(WorldFacade duelWorld, CountDownLatch insideTick, CountDownLatch proceed) {
        Thread caller = Thread.currentThread();
        for (int attempt = 0; attempt < HOLD_ATTEMPTS; attempt++) {
            AtomicBoolean ranOnCaller = new AtomicBoolean();
            duelWorld.inboxBarrier().thenRun(() -> {
                if (Thread.currentThread().equals(caller)) {
                    ranOnCaller.set(true);
                    return;
                }
                insideTick.countDown();
                awaitQuietly(proceed);
                arena.handleCharacterDefeated(FIRST_ID, SECOND_ID);
            });
            if (!ranOnCaller.get()) {
                return;
            }
        }
        throw new AssertionError("không gắn được callback vào tick thread sau " + HOLD_ATTEMPTS + " lần");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("hết giờ chờ");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi chờ", e);
        }
    }

    private static PvpRatingPort noOpRatingPort() {
        return new PvpRatingPort() {
            @Override
            public Optional<PvpRating> findByCharacterId(long characterId) {
                return Optional.empty();
            }

            @Override
            public EloRating.Result recordDuelResult(
                    long winnerCharacterId, long loserCharacterId, EloComputer computer) {
                return computer.compute(EloRating.DEFAULT_RATING, EloRating.DEFAULT_RATING);
            }
        };
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("hết giờ chờ điều kiện");
            }
            Thread.sleep(POLL_MILLIS);
        }
    }
}
