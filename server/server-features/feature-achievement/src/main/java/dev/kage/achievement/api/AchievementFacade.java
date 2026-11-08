package dev.kage.achievement.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.kage.achievement.application.AchievementUseCase;
import dev.kage.achievement.domain.Achievement;
import dev.kage.achievement.domain.AchievementKind;
import dev.kage.achievement.domain.AchievementPort;

/** API của {@code feature-achievement} (ADR-062). Mọi hàm chạm DB — chỉ gọi trên Virtual Thread. */
public final class AchievementFacade {

    /** Composition không có thành tựu (test) — không thành tựu nào, không ghi gì. */
    public static final AchievementFacade DISABLED =
            new AchievementFacade(new AchievementUseCase(new NoAchievements()));

    private final AchievementUseCase useCases;

    /** Dựng ở composition root. */
    public AchievementFacade(AchievementUseCase useCases) {
        this.useCases = useCases;
    }

    /** Một sự kiện đếm được — trả thành tựu VỪA mở. */
    public List<Achievement> count(long characterId, AchievementKind kind, long delta) {
        return useCases.count(characterId, kind, delta);
    }

    /** Nhân vật vừa lên cấp — trả thành tựu cấp VỪA mở. */
    public List<Achievement> levelReached(long characterId, int level) {
        return useCases.levelReached(characterId, level);
    }

    /** Mở bù mọi mốc đã đạt mà chưa mở — gọi mỗi lần vào world. */
    public List<Achievement> reconcile(long characterId, int level) {
        return useCases.reconcile(characterId, level);
    }

    /** Cửa sổ thành tựu: mọi dòng + tiến độ + danh hiệu đang đeo. */
    public AchievementUseCase.Sheet sheet(long characterId, int level) {
        return useCases.sheet(characterId, level);
    }

    /** Đeo danh hiệu đã mở; mã rỗng = bỏ. {@code false} nếu chưa mở. */
    public boolean wearTitle(long characterId, String achievementCode) {
        return useCases.wearTitle(characterId, achievementCode);
    }

    /** Mã thành tựu có danh hiệu đang đeo, rỗng nếu không đeo gì. */
    public Optional<String> activeTitleCode(long characterId) {
        return useCases.activeTitleCode(characterId);
    }

    /** ADR-223 — bộ đếm cộng dồn (hạ quái, trả nhiệm vụ, thắng luận võ, điểm danh). */
    public Map<AchievementKind, Long> counters(long characterId) {
        return useCases.counters(characterId);
    }

    /** ADR-223 — số thành tựu đã mở của {@code characterId}. */
    public int unlockedCount(long characterId) {
        return useCases.unlockedCount(characterId);
    }

    /** ADR-209 — số thành tựu đã mở của mọi nhân vật (bảng xếp hạng Thành tựu). */
    public Map<Long, Integer> unlockedCounts() {
        return useCases.unlockedCounts();
    }

    /** Khoá i18n danh hiệu đang đeo, rỗng nếu không đeo gì. */
    public Optional<String> activeTitleKey(long characterId) {
        return useCases.activeTitleKey(characterId);
    }

    /** Bộ nhớ rỗng cho {@link #DISABLED}: không định nghĩa nào ⇒ không bao giờ mở gì. */
    private static final class NoAchievements implements AchievementPort {
        @Override
        public List<Achievement> definitions() {
            return List.of();
        }

        @Override
        public long addToCounter(long characterId, AchievementKind kind, long delta) {
            return delta;
        }

        @Override
        public Map<AchievementKind, Long> counters(long characterId) {
            return Map.of();
        }

        @Override
        public boolean unlock(long characterId, String achievementCode) {
            return false;
        }

        @Override
        public Map<Long, Integer> unlockedCounts() {
            return Map.of();
        }

        @Override
        public Set<String> unlocked(long characterId) {
            return Set.of();
        }

        @Override
        public Optional<String> activeTitle(long characterId) {
            return Optional.empty();
        }

        @Override
        public boolean setActiveTitle(long characterId, String achievementCode) {
            return false;
        }

        @Override
        public void clearActiveTitle(long characterId) {
            // không có gì để xoá
        }
    }
}
