package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Chốt trận Arena khi người chơi RỜI MẠNG — đo giá trị cuối: ai còn đứng trong world chính, rating
 * ghi mấy lần. Đường chốt chạy trên {@code settlementExecutor}, nên mọi khẳng định chờ tới lúc
 * {@link PvpRatingPort#recordDuelResult} được gọi — bước CUỐI của lượt chốt, sau khi đã đưa người về.
 */
@Timeout(60)
class ArenaSettlementLifecycleTest {

    private static final long FIRST_ID = 301L;
    private static final long SECOND_ID = 302L;
    private static final long WAIT_MILLIS = 10_000;
    private static final long POLL_MILLIS = 10;

    /** Đủ lâu để một lượt chốt thứ hai (nếu có) chạy xong — nó không chạm DB nào trước vé chốt. */
    private static final long SECOND_SETTLEMENT_GRACE_MILLIS = 300;

    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private ArenaInstanceRegistry arena;
    private final AtomicInteger ratingWrites = new AtomicInteger();
    private final CountDownLatch ratingWritten = new CountDownLatch(1);

    /** Chặn lần đọc chỉ số của {@link #SECOND_ID} khi bật — tức ngay lúc lượt chốt đưa họ về world chính. */
    private volatile boolean holdSecondReturn;

    private final CountDownLatch secondReturnStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSecondReturn = new CountDownLatch(1);

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
                DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(this::onLoadStats),
                new PvpFacade(countingRatingPort()),
                mainWorldChannels);
    }

    @AfterEach
    void tearDown() {
        releaseSecondReturn.countDown();
        arena.stopAll();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    /**
     * Cả hai rớt mạng gần như cùng lúc. Người rớt TRƯỚC thua, lượt chốt đưa người kia về world chính —
     * nhưng người kia cũng vừa rớt: {@code channelInactive} của họ đã chạy trọn (trận đã chốt nên Arena
     * trả {@code false}, gỡ khỏi world chính lúc họ chưa ở đó). Bản cũ vẫn gắn họ vào world chính sau
     * đó — thực thể bóng ma không kết nối, không ai dọn, quái vẫn đánh, chiếm chỗ khu vực.
     */
    @Test
    void shouldNotLeaveAGhostInTheMainWorld_whenBothDuelistsDisconnect() throws Exception {
        startDuel();
        holdSecondReturn = true;

        simulateDisconnect(FIRST_ID);
        assertThat(secondReturnStarted.await(WAIT_MILLIS, TimeUnit.MILLISECONDS))
                .as("lượt chốt phải đang đưa người thắng về")
                .isTrue();
        simulateDisconnect(SECOND_ID);
        releaseSecondReturn.countDown();

        assertThat(ratingWritten.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(mainWorldChannels.worldFacadeFor(SECOND_ID)).isEmpty();
        assertThat(mainWorldChannels.worldFacadeFor(FIRST_ID)).isEmpty();
        assertThat(mainWorldChannels.playerCount()).isZero();
        assertThat(ratingWrites).hasValue(1);
    }

    /**
     * Hai người gục CÙNG một tick (double KO) — hai lời báo gục liên tiếp trên tick thread. Đúng một
     * lượt chốt được ghi rating; cả hai (còn online) về world chính đúng một lần.
     */
    @Test
    void shouldRecordExactlyOneResult_whenBothDuelistsFallInTheSameTick() throws Exception {
        startDuel();

        arena.handleCharacterDefeated(FIRST_ID, SECOND_ID);
        arena.handleCharacterDefeated(SECOND_ID, FIRST_ID);

        assertThat(ratingWritten.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        Thread.sleep(SECOND_SETTLEMENT_GRACE_MILLIS);
        assertThat(ratingWrites).hasValue(1);
        assertThat(arena.worldFacadeFor(FIRST_ID)).isEmpty();
        assertThat(arena.worldFacadeFor(SECOND_ID)).isEmpty();
        assertThat(mainWorldChannels.worldFacadeFor(FIRST_ID)).isPresent();
        assertThat(mainWorldChannels.worldFacadeFor(SECOND_ID)).isPresent();
        assertThat(mainWorldChannels.playerCount()).isEqualTo(2);
    }

    /**
     * Đối thủ rớt mạng giữa trận: người rớt THUA (feature-pvp/CLAUDE.md — không được "trốn thua"), người
     * còn lại về world chính, rating ghi đúng một lần.
     */
    @Test
    void shouldAwardTheWinToTheStayingDuelist_whenTheOpponentDisconnects() throws Exception {
        startDuel();

        simulateDisconnect(FIRST_ID);

        assertThat(ratingWritten.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(ratingWrites).hasValue(1);
        assertThat(mainWorldChannels.worldFacadeFor(SECOND_ID)).isPresent();
        assertThat(mainWorldChannels.worldFacadeFor(FIRST_ID)).isEmpty();
    }

    private void startDuel() {
        long challengeId = arena.challengeDuel(FIRST_ID, SECOND_ID).challengeId();
        assertThat(arena.acceptDuel(SECOND_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);
        waitUntil(() -> arena.worldFacadeFor(FIRST_ID).isPresent()
                && arena.worldFacadeFor(SECOND_ID).isPresent());
    }

    /** Đúng thứ tự của {@code GatewayChannelHandler.releaseCharacter}: gỡ đăng ký → Arena → world chính. */
    private void simulateDisconnect(long characterId) {
        characterRegistry.unregister(characterId);
        if (!arena.handleDisconnectIfInDuel(characterId)) {
            mainWorldChannels.detachCharacter(characterId);
        }
    }

    private void onLoadStats(long characterId) {
        if (characterId != SECOND_ID || !holdSecondReturn) {
            return;
        }
        secondReturnStarted.countDown();
        try {
            releaseSecondReturn.await(WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi giữ lượt đưa về", e);
        }
    }

    private PvpRatingPort countingRatingPort() {
        return new PvpRatingPort() {
            @Override
            public Optional<PvpRating> findByCharacterId(long characterId) {
                return Optional.empty();
            }

            @Override
            public EloRating.Result recordDuelResult(
                    long winnerCharacterId, long loserCharacterId, EloComputer computer) {
                ratingWrites.incrementAndGet();
                ratingWritten.countDown();
                return computer.compute(EloRating.DEFAULT_RATING, EloRating.DEFAULT_RATING);
            }
        };
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("điều kiện không thành sau " + WAIT_MILLIS + "ms");
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("bị ngắt khi chờ", e);
            }
        }
    }
}
