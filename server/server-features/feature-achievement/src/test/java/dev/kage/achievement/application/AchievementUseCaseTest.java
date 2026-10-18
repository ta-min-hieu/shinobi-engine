package dev.kage.achievement.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.achievement.domain.Achievement;
import dev.kage.achievement.domain.AchievementKind;
import dev.kage.achievement.domain.AchievementPort;

/** Luật mở thành tựu (ADR-062): mở khi vượt mốc, không báo đôi, bù lúc vào world, đeo chỉ khi đã mở. */
class AchievementUseCaseTest {

    private static final long HERO = 7L;
    private static final Achievement SLAYER_10 =
            new Achievement("slayer_10", AchievementKind.KILL_MONSTERS, 10, "a.slayer_10", "title.slayer_10");
    private static final Achievement SLAYER_100 =
            new Achievement("slayer_100", AchievementKind.KILL_MONSTERS, 100, "a.slayer_100", "title.slayer_100");
    private static final Achievement LEVEL_10 =
            new Achievement("level_10", AchievementKind.REACH_LEVEL, 10, "a.level_10", "title.level_10");

    private final FakePort port = new FakePort();
    private final AchievementUseCase useCases = new AchievementUseCase(port);

    @Test
    void shouldUnlockExactlyWhenTheCounterCrossesTheTarget_andNeverAgain() {
        for (int i = 0; i < 9; i++) {
            assertThat(useCases.count(HERO, AchievementKind.KILL_MONSTERS, 1)).isEmpty();
        }

        assertThat(useCases.count(HERO, AchievementKind.KILL_MONSTERS, 1)).containsExactly(SLAYER_10);
        assertThat(useCases.count(HERO, AchievementKind.KILL_MONSTERS, 1)).isEmpty();
        assertThat(port.unlockCalls).as("chỉ thử mở đúng lần vượt mốc").isEqualTo(1);
    }

    @Test
    void reconcileShouldOpenMissedMilestones_includingLevelOnes() {
        port.counters.put(AchievementKind.KILL_MONSTERS, 150L);

        List<Achievement> opened = useCases.reconcile(HERO, 12);

        assertThat(opened).containsExactlyInAnyOrder(SLAYER_10, SLAYER_100, LEVEL_10);
        assertThat(useCases.reconcile(HERO, 12)).isEmpty();
    }

    @Test
    void titleCanOnlyBeWornOnceUnlocked_andEmptyCodeTakesItOff() {
        assertThat(useCases.wearTitle(HERO, "slayer_10")).isFalse();
        useCases.levelReached(HERO, 10);

        assertThat(useCases.wearTitle(HERO, "level_10")).isTrue();
        assertThat(useCases.activeTitleKey(HERO)).contains("title.level_10");
        assertThat(useCases.wearTitle(HERO, "")).isTrue();
        assertThat(useCases.activeTitleKey(HERO)).isEmpty();
    }

    @Test
    void sheetShouldShowProgressFromCountersAndLevel() {
        port.counters.put(AchievementKind.KILL_MONSTERS, 42L);

        AchievementUseCase.Sheet sheet = useCases.sheet(HERO, 5);

        assertThat(sheet.rows())
                .extracting(AchievementUseCase.AchievementView::progress)
                .containsExactly(42L, 42L, 5L);
    }

    private static final class FakePort implements AchievementPort {
        final Map<AchievementKind, Long> counters = new EnumMap<>(AchievementKind.class);
        final Set<String> unlocked = new HashSet<>();
        Optional<String> active = Optional.empty();
        int unlockCalls;

        @Override
        public List<Achievement> definitions() {
            return List.of(SLAYER_10, SLAYER_100, LEVEL_10);
        }

        @Override
        public long addToCounter(long characterId, AchievementKind kind, long delta) {
            return counters.merge(kind, delta, Long::sum);
        }

        @Override
        public Map<AchievementKind, Long> counters(long characterId) {
            return Map.copyOf(counters);
        }

        @Override
        public boolean unlock(long characterId, String achievementCode) {
            unlockCalls++;
            return unlocked.add(achievementCode);
        }

        @Override
        public Set<String> unlocked(long characterId) {
            return Set.copyOf(unlocked);
        }

        @Override
        public Optional<String> activeTitle(long characterId) {
            return active;
        }

        @Override
        public boolean setActiveTitle(long characterId, String achievementCode) {
            if (!unlocked.contains(achievementCode)) {
                return false;
            }
            active = Optional.of(achievementCode);
            return true;
        }

        @Override
        public void clearActiveTitle(long characterId) {
            active = Optional.empty();
        }
    }
}
