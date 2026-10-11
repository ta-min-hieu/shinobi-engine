package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
 * Sau {@code stopAll()} không World hầm ngục / đấu trường nào được dựng thêm (sửa 2026-10-11).
 *
 * <p>{@code MainWorldChannelRegistry} có cờ {@code stopped} từ 2026-10-03, hai registry instance thì
 * chưa: lệnh vào hầm ngục hoặc lời chấp nhận thách đấu còn sống qua lúc tắt server — tới muộn, hay
 * đang đọc DB ngoài khoá đúng lúc {@code stopAll()} chạy — vẫn dựng và {@code start()} một World mới.
 * Không ai dừng World đó nữa: nó tick tiếp trong lúc pool DB đóng. Đấu trường còn giữ nguyên các lời
 * thách đang chờ qua {@code stopAll()}, nên chấp nhận một lời thách cũ là đủ để dựng trận.
 *
 * <p>Đo GIÁ TRỊ CUỐI: không định tuyến, không instance, không sổ sách nào còn lại.
 */
@Timeout(60)
class InstanceRegistriesAfterShutdownTest {

    private static final long LEADER_ID = DungeonInstanceRegistryConcurrencyTest.LEADER_ID;
    private static final long MEMBER_ID = DungeonInstanceRegistryConcurrencyTest.MEMBER_ID;
    /** Người ở world chính, ngoài nhóm. */
    private static final long OUTSIDER_ID = 103L;

    private static final String DUNGEON_CODE = "sample_dungeon";

    /** Chạy ở lần đọc chỉ số kế tiếp — tức giữa lượt kiểm ngoài khoá và phần ghi trong khoá. */
    private final AtomicReference<Runnable> duringDatabaseRead = new AtomicReference<>(() -> {});

    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private ArenaInstanceRegistry arena;
    private DungeonInstanceRegistry dungeons;

    @BeforeEach
    void setUp() {
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                null,
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        for (long id : new long[] {LEADER_ID, MEMBER_ID, OUTSIDER_ID}) {
            characterRegistry.register(
                    id,
                    new EmbeddedChannel(new ChannelInboundHandlerAdapter())
                            .pipeline()
                            .firstContext());
        }
        WorldFacade firstChannel = new WorldFacade(
                characterRegistry, false, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry,
                firstChannel,
                new SimpleMeterRegistry(),
                TestWorldContents.trainingGroundWithElder());
        arena = new ArenaInstanceRegistry(
                characterRegistry,
                DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(this::runDatabaseReadHook),
                new PvpFacade(new FixedRatingPort()),
                mainWorldChannels);
        dungeons = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(this::runDatabaseReadHook),
                        DungeonInstanceRegistryConcurrencyTest.fakePartyFacade(() -> {}),
                        arena::isInDuel),
                mainWorldChannels,
                TestWorldContents.withBoss());
        // Cùng cách GatewayBootstrap nối hai registry.
        arena.refuseCharactersIn(dungeons::isInDungeon);
    }

    /** Gọi lại {@code stopAll()} là để dừng World rò rỉ khi test đỏ — khi xanh thì không còn gì để dừng. */
    @AfterEach
    void tearDown() {
        dungeons.stopAll();
        arena.stopAll();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void shouldNotStartADungeonWorld_whenEnteringAfterStopAll() {
        dungeons.stopAll();

        dungeons.enterDungeon(LEADER_ID, DUNGEON_CODE);

        assertNoDungeonLeft();
    }

    @Test
    void shouldNotStartADungeonWorld_whenStopAllRunsWhileTheEnterIsReadingTheDatabase() {
        stopOnNextDatabaseRead(dungeons::stopAll);

        dungeons.enterDungeon(LEADER_ID, DUNGEON_CODE);

        assertNoDungeonLeft();
    }

    @Test
    void shouldNotStartADuelWorld_whenAcceptingAChallengeAfterStopAll() {
        long challengeId = arena.challengeDuel(LEADER_ID, OUTSIDER_ID).challengeId();
        arena.stopAll();

        assertThat(arena.acceptDuel(OUTSIDER_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.NOT_FOUND);

        assertNoDuelLeft();
    }

    @Test
    void shouldNotStartADuelWorld_whenStopAllRunsWhileTheAcceptIsReadingTheDatabase() {
        long challengeId = arena.challengeDuel(LEADER_ID, OUTSIDER_ID).challengeId();
        stopOnNextDatabaseRead(arena::stopAll);

        assertThat(arena.acceptDuel(OUTSIDER_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.NOT_FOUND);

        assertNoDuelLeft();
    }

    private void assertNoDungeonLeft() {
        assertThat(dungeons.instanceCount())
                .as("không World hầm ngục nào được dựng sau stopAll()")
                .isZero();
        assertThat(dungeons.worldFacadeFor(LEADER_ID)).isEmpty();
        assertThat(dungeons.worldFacadeFor(MEMBER_ID)).isEmpty();
        assertThat(dungeons.trackedEntryCount()).as("không sổ sách nào còn lại").isZero();
    }

    private void assertNoDuelLeft() {
        assertThat(arena.worldFacadeFor(LEADER_ID))
                .as("không World đấu trường nào được dựng sau stopAll()")
                .isEmpty();
        assertThat(arena.worldFacadeFor(OUTSIDER_ID)).isEmpty();
        assertThat(arena.trackedEntryCount()).as("không sổ sách nào còn lại").isZero();
    }

    /** Hook chỉ chạy MỘT lần: lượt đọc DB của người thứ hai không được gọi lại {@code stopAll()}. */
    private void stopOnNextDatabaseRead(Runnable stopAll) {
        duringDatabaseRead.set(stopAll);
    }

    private void runDatabaseReadHook(long characterId) {
        duringDatabaseRead.getAndSet(() -> {}).run();
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
