package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.ExperienceProgress;
import dev.kage.inventory.domain.DropTableEntry;

/**
 * Ba việc sau một cú hạ quái — rơi đồ, tiến độ quest, EXP — khi MỘT việc hỏng, và khi server đang tắt.
 *
 * <p>Mỗi việc chạm DB trên một Virtual Thread riêng. Test đo thứ các cổng THẬT SỰ nhận được (số lượt
 * gọi) sau khi {@code stop()} đã chờ mọi lượt xong — "không ném gì" không phải bằng chứng là phần
 * thưởng đã tới nơi.
 */
@Timeout(10)
class KillRewardIsolationTest {

    private static final long KILLER_ID = 7L;
    private static final long EXP_REWARD = 20L;
    private static final String MONSTER_CODE = "training_dummy";

    /** Việc nào trong ba việc bị làm cho hỏng — tên khớp nhãn {@code step=} trong log. */
    enum FailingStep {
        LOOT,
        QUEST_PROGRESS,
        EXPERIENCE
    }

    private final AtomicInteger lootRolls = new AtomicInteger();
    private final AtomicInteger questEvents = new AtomicInteger();
    private final AtomicInteger experienceGrants = new AtomicInteger();

    private ConnectedCharacterRegistry registry;

    @AfterEach
    void stopRegistry() {
        if (registry != null) {
            registry.stop();
        }
    }

    /**
     * Một việc ném (DB chập chờn) thì hai việc còn lại vẫn phải chạy, và lỗi phải ra log có cấu trúc —
     * kèm việc nào hỏng, của ai, con quái nào — chứ không chết im trong Virtual Thread.
     */
    @ParameterizedTest
    @EnumSource(FailingStep.class)
    void oneFailingStepShouldNeitherCancelTheOthersNorFailSilently(FailingStep failing) {
        registry = registryWhere(failing);

        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
            registry.stop(); // chờ cả ba lượt chạy xong

            assertThat(new int[] {lootRolls.get(), questEvents.get(), experienceGrants.get()})
                    .as("cả ba việc đều phải được THỬ, dù %s ném", failing)
                    .containsExactly(1, 1, 1);
            // WARN từ 2026-10-17 (dòng 78(d)) — mất một phần thưởng không phải sự cố đánh thức trực ban.
            assertThat(errorLog.errorsSince(0)).as("không còn log ERROR").isEmpty();
            assertThat(errorLog.warningsSince(0))
                    .as("phần thưởng mất phải để lại đúng một dòng WARN nói rõ mất gì, của ai")
                    .singleElement()
                    .asString()
                    .contains("step=" + failing.name())
                    .contains("characterId=" + KILLER_ID)
                    .contains("monsterCode=" + MONSTER_CODE)
                    .contains("simulated database outage");
        }
    }

    /** Hai cú hạ liền nhau: lượt đầu hỏng ở một việc không được làm lượt sau mất gì. */
    @Test
    void failedRewardOfOneKillShouldNotPoisonTheNextKill() {
        AtomicInteger lootCalls = new AtomicInteger();
        registry = new ConnectedCharacterRegistry(
                ConnectedCharacterRegistryTest.fakeInventoryFacade(dropTableCode -> {
                    if (lootCalls.incrementAndGet() == 1) {
                        throw new IllegalStateException("simulated database outage");
                    }
                    return List.<DropTableEntry>of();
                }),
                ConnectedCharacterRegistryTest.fakeQuestFacade(questEvents::incrementAndGet),
                this::countExperienceGrant,
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));

        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
            registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
            registry.stop();

            assertThat(errorLog.warningsSince(0))
                    .as("chỉ lượt rơi đồ hỏng để lại WARN")
                    .hasSize(1);
        }

        assertThat(new int[] {lootCalls.get(), questEvents.get(), experienceGrants.get()})
                .containsExactly(2, 2, 2);
    }

    /**
     * Người chơi ngắt kết nối TRƯỚC khi lượt trao thưởng chạy: phần thưởng của con quái đã chết vẫn
     * phải được ghi — chỉ mất gói thông báo.
     */
    @Test
    void rewardShouldStillBeWritten_whenTheKillerDisconnectedBeforeItRan() {
        registry = registryWhere(null);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry.register(KILLER_ID, channel.pipeline().firstContext());
        registry.unregister(KILLER_ID);

        registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
        registry.stop();

        assertThat(new int[] {lootRolls.get(), questEvents.get(), experienceGrants.get()})
                .containsExactly(1, 1, 1);
        assertThat(channel.outboundMessages())
                .as("không gửi gì cho kết nối đã dọn")
                .isEmpty();
    }

    /**
     * Một World còn tick sau khi registry đã {@code stop()} (trình tự tắt chạy hai lần, hoặc một World
     * dựng muộn) báo quái chết: executor đã tắt, lời báo bị từ chối. Exception đó không được văng lên
     * tick thread — {@code WorldFacade.tickOnce} bắt được nhưng BỎ phần còn lại của tick đó — và không
     * việc nào được chạy nửa vời trên tick thread (cấm blocking I/O ở đó, pool DB cũng sắp đóng).
     */
    @Test
    void killReportedAfterStopShouldBeDroppedWithoutThrowingOnTheTickThread() {
        registry = registryWhere(null);
        registry.stop();

        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            assertThatCode(() -> registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD))
                    .doesNotThrowAnyException();

            assertThat(new int[] {lootRolls.get(), questEvents.get(), experienceGrants.get()})
                    .as("không việc nào chạy trên luồng gọi (tick thread)")
                    .containsExactly(0, 0, 0);
            assertThat(errorLog.errorsSince(0))
                    .as("server đang tắt không phải sự cố — không log ERROR")
                    .isEmpty();
        }
    }

    private ConnectedCharacterRegistry registryWhere(FailingStep failing) {
        return new ConnectedCharacterRegistry(
                ConnectedCharacterRegistryTest.fakeInventoryFacade(dropTableCode -> {
                    lootRolls.incrementAndGet();
                    failIf(failing == FailingStep.LOOT);
                    return List.<DropTableEntry>of();
                }),
                ConnectedCharacterRegistryTest.fakeQuestFacade(() -> {
                    questEvents.incrementAndGet();
                    failIf(failing == FailingStep.QUEST_PROGRESS);
                }),
                (characterId, amount) -> {
                    GrantExperienceUseCase.Result result = countExperienceGrant(characterId, amount);
                    failIf(failing == FailingStep.EXPERIENCE);
                    return result;
                },
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
    }

    private GrantExperienceUseCase.Result countExperienceGrant(long characterId, long amount) {
        experienceGrants.incrementAndGet();
        return new GrantExperienceUseCase.Result(new ExperienceProgress(1, amount, amount, 100L), false);
    }

    private static void failIf(boolean shouldFail) {
        if (shouldFail) {
            throw new IllegalStateException("simulated database outage");
        }
    }
}
