package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.CharacterEntry;
import dev.kage.server.world.Element;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Một nhân vật chỉ được ở MỘT khu vực map chính (quyết định chủ dự án 2026-09-25: một nhân vật chỉ
 * một phiên sống).
 *
 * <p>Bug trước đó: {@code attachCharacter} không hỏi nhân vật đã ở khu vực nào chưa. Cùng khu vực
 * thì world tự bỏ qua lệnh trùng, nhưng khu vực đầu ĐẦY thì lần gắn thứ hai rơi sang khu vực khác —
 * cùng một nhân vật có HAI thực thể ở hai world (cày gấp đôi), và {@code channelByCharacterId} chỉ
 * còn nhớ một, nên lần detach sau bỏ sót thực thể kia mãi mãi.
 */
@Timeout(30)
class MainWorldChannelRegistryTest {

    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    private static final long CHARACTER_ID = 1_000L;
    private static final long TICK_OBSERVATION_MILLIS = 300L;

    private MainWorldChannelRegistry channels;
    private ConnectedCharacterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                GatewayPipelineTest.fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        channels = new MainWorldChannelRegistry(
                registry, new WorldFacade(registry), new SimpleMeterRegistry(), WorldContentCatalog.EMPTY);
    }

    @AfterEach
    void tearDown() {
        channels.stopAll();
    }

    @Test
    void shouldKeepOneMembership_whenSameCharacterIsAttachedAgainAfterFirstChannelFilledUp() {
        WorldFacade first = channels.attachCharacter(entry(CHARACTER_ID));
        for (long filler = 1; filler < MainWorldChannelRegistry.DEFAULT_CAPACITY; filler++) {
            channels.attachCharacter(entry(filler));
        }
        int before = channels.playerCount();

        WorldFacade second = channels.attachCharacter(entry(CHARACTER_ID));

        assertThat(second).isSameAs(first);
        assertThat(channels.playerCount()).isEqualTo(before);
        assertThat(channels.worldFacadeFor(CHARACTER_ID)).containsSame(first);
    }

    @Test
    void shouldAttachAgain_afterCharacterWasDetached() {
        channels.attachCharacter(entry(CHARACTER_ID));
        channels.detachCharacter(CHARACTER_ID);

        channels.attachCharacter(entry(CHARACTER_ID));

        assertThat(channels.playerCount()).isEqualTo(1);
        assertThat(channels.worldFacadeFor(CHARACTER_ID)).isPresent();
    }

    /**
     * EventLoop xin khoá của registry cho MỌI gói di chuyển ({@code worldFacadeFor}), còn dựng một
     * {@link WorldFacade} tốn ~55–110ms (chạy thử script skill trong GraalJS) — bản cũ dựng khu vực
     * mới NGAY TRONG khoá, mọi EventLoop đứng chờ chừng ấy mỗi lần mở khu vực (SERVER.md §2).
     *
     * <p>Móc vào lúc dựng: {@link MicrometerTickMetricsPort} đăng ký Timer ngay trong lúc dựng World
     * của khu vực mới, nên {@code newTimer} chạy trên đúng luồng đang dựng — hỏi luồng đó có đang giữ
     * khoá của registry không.
     */
    @Test
    void shouldBuildNewChannelWorldOutsideRegistryLock_whenAttachCrossesAutoOpenThreshold() {
        LockProbingMeterRegistry meters = new LockProbingMeterRegistry();
        MainWorldChannelRegistry probed =
                new MainWorldChannelRegistry(registry, new WorldFacade(registry), meters, WorldContentCatalog.EMPTY);
        meters.watch(probed);
        try {
            for (long id = 1; id <= MainWorldChannelRegistry.DEFAULT_CAPACITY; id++) {
                probed.attachCharacter(entry(id));
            }

            assertThat(meters.timersCreated.get())
                    .as("khu vực mới đã được dựng")
                    .isPositive();
            assertThat(meters.timersCreatedUnderLock.get()).isZero();
            assertThat(probed.playerCount()).isEqualTo(MainWorldChannelRegistry.DEFAULT_CAPACITY);
        } finally {
            probed.stopAll();
        }
    }

    /** Đường mở sẵn cho caller đang giữ khoá riêng của nó (DungeonInstanceRegistry) — cùng luật. */
    @Test
    void shouldBuildNewChannelWorldOutsideRegistryLock_whenRoomIsEnsuredAhead() {
        LockProbingMeterRegistry meters = new LockProbingMeterRegistry();
        MainWorldChannelRegistry probed =
                new MainWorldChannelRegistry(registry, new WorldFacade(registry), meters, WorldContentCatalog.EMPTY);
        meters.watch(probed);
        try {
            long belowThreshold = MainWorldChannelRegistry.DEFAULT_CAPACITY * 4L / 5L;
            for (long id = 1; id <= belowThreshold; id++) {
                probed.attachCharacter(entry(id));
            }
            assertThat(meters.timersCreated.get())
                    .as("chưa tới lượt gắn kế tiếp")
                    .isZero();

            probed.ensureRoomForOneMore();
            int afterEnsure = meters.timersCreated.get();
            probed.attachCharacter(entry(belowThreshold + 1));

            assertThat(afterEnsure)
                    .as("khu vực mới dựng ở ensureRoomForOneMore")
                    .isPositive();
            assertThat(meters.timersCreated.get())
                    .as("lượt gắn không dựng thêm")
                    .isEqualTo(afterEnsure);
            assertThat(meters.timersCreatedUnderLock.get()).isZero();
        } finally {
            probed.stopAll();
        }
    }

    /**
     * Gắn nhân vật SAU {@code stopAll} không được mở (và start) một khu vực mới (2026-10-03).
     *
     * <p>Lúc tắt server, {@code GatewayBootstrap} dừng world chính TRƯỚC đấu trường/hầm ngục. Lượt chốt
     * trận do {@code channelInactive} xếp lên {@code settlementExecutor} còn chạy sau đó và đưa người
     * thắng về world chính: danh sách khu vực đã rỗng ⇒ "cần khu vực mới" ⇒ dựng + start một World mà
     * không ai còn dừng nữa — nó tick tiếp trong lúc pool DB đóng. Đo bằng chính nhịp tick: Timer
     * {@code kage.tick.duration} của khu vực mở thêm không được tăng sau khi đã tắt.
     */
    @Test
    void shouldNotStartAnyWorld_whenCharacterIsAttachedAfterStopAll() throws InterruptedException {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        MainWorldChannelRegistry stopped =
                new MainWorldChannelRegistry(registry, new WorldFacade(registry), meters, WorldContentCatalog.EMPTY);
        stopped.stopAll();

        stopped.ensureRoomForOneMore();
        stopped.attachCharacter(entry(CHARACTER_ID));
        long ticksAfterAttach = tickCount(meters);
        Thread.sleep(TICK_OBSERVATION_MILLIS);

        assertThat(tickCount(meters)).isEqualTo(ticksAfterAttach).isZero();
        assertThat(stopped.playerCount()).isZero();
    }

    private static long tickCount(SimpleMeterRegistry meters) {
        return meters.find("kage.tick.duration").timers().stream()
                .mapToLong(Timer::count)
                .sum();
    }

    /** Đếm Timer mới, và bao nhiêu cái được tạo trong lúc luồng tạo đang giữ khoá của registry. */
    private static final class LockProbingMeterRegistry extends SimpleMeterRegistry {
        private final AtomicInteger timersCreated = new AtomicInteger();
        private final AtomicInteger timersCreatedUnderLock = new AtomicInteger();
        private volatile Object watchedLock = new Object();

        void watch(Object lock) {
            watchedLock = lock;
        }

        @Override
        protected Timer newTimer(
                Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
            timersCreated.incrementAndGet();
            if (Thread.holdsLock(watchedLock)) {
                timersCreatedUnderLock.incrementAndGet();
            }
            return super.newTimer(id, distributionStatisticConfig, pauseDetector);
        }
    }

    private static CharacterEntry entry(long characterId) {
        return CharacterEntry.withoutEquipment(characterId, 0f, 0f, STATS);
    }
}
