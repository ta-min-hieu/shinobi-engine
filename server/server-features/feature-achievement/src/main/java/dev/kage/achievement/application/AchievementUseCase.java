package dev.kage.achievement.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.kage.achievement.domain.Achievement;
import dev.kage.achievement.domain.AchievementKind;
import dev.kage.achievement.domain.AchievementPort;

/**
 * Ghi tiến độ, mở thành tựu, đeo danh hiệu (ADR-062).
 *
 * <p><b>Mở khi VƯỢT mốc, bù lúc vào world.</b> Mỗi sự kiện chỉ thử mở những mốc vừa bị vượt ở lần cộng
 * này ({@code trước < mốc <= sau}) — một lần hạ quái là một UPSERT, không phải thêm một lượt đọc. Tiến
 * trình chết giữa "cộng" và "mở" thì mốc đó bị lỡ; {@link #reconcile} chạy mỗi lần vào world sẽ mở bù
 * mọi mốc đã đạt mà chưa mở (kể cả mốc cấp của nhân vật có từ trước tính năng này).
 */
public final class AchievementUseCase {

    /** Một dòng của cửa sổ thành tựu. */
    public record AchievementView(Achievement achievement, long progress, boolean unlocked) {}

    /** Toàn bộ cửa sổ thành tựu của một nhân vật. */
    public record Sheet(List<AchievementView> rows, Optional<String> activeTitleCode) {
        public Sheet {
            rows = List.copyOf(rows);
        }
    }

    private final AchievementPort port;

    public AchievementUseCase(AchievementPort port) {
        this.port = port;
    }

    /**
     * Một sự kiện đếm được (hạ quái, lĩnh thưởng, thắng trận, điểm danh).
     *
     * @return thành tựu VỪA mở ở lượt này (thường rỗng)
     */
    public List<Achievement> count(long characterId, AchievementKind kind, long delta) {
        if (!kind.isCounted() || delta <= 0) {
            throw new IllegalArgumentException("Không phải sự kiện đếm được: " + kind + " +" + delta);
        }
        long after = port.addToCounter(characterId, kind, delta);
        long before = after - delta;
        List<Achievement> opened = new ArrayList<>();
        for (Achievement achievement : port.definitions()) {
            if (achievement.kind() == kind
                    && achievement.reachedBy(after)
                    && !achievement.reachedBy(before)
                    && port.unlock(characterId, achievement.code())) {
                opened.add(achievement);
            }
        }
        return opened;
    }

    /** Nhân vật vừa lên cấp {@code level}. */
    public List<Achievement> levelReached(long characterId, int level) {
        List<Achievement> opened = new ArrayList<>();
        for (Achievement achievement : port.definitions()) {
            if (achievement.kind() == AchievementKind.REACH_LEVEL
                    && achievement.reachedBy(level)
                    && port.unlock(characterId, achievement.code())) {
                opened.add(achievement);
            }
        }
        return opened;
    }

    /** Mở bù mọi mốc đã đạt mà chưa mở — gọi mỗi lần vào world. */
    public List<Achievement> reconcile(long characterId, int level) {
        Map<AchievementKind, Long> counters = port.counters(characterId);
        Set<String> unlocked = port.unlocked(characterId);
        List<Achievement> opened = new ArrayList<>();
        for (Achievement achievement : port.definitions()) {
            if (!unlocked.contains(achievement.code())
                    && achievement.reachedBy(progressOf(achievement.kind(), counters, level))
                    && port.unlock(characterId, achievement.code())) {
                opened.add(achievement);
            }
        }
        return opened;
    }

    public Sheet sheet(long characterId, int level) {
        Map<AchievementKind, Long> counters = port.counters(characterId);
        Set<String> unlocked = port.unlocked(characterId);
        List<AchievementView> rows = new ArrayList<>();
        for (Achievement achievement : port.definitions()) {
            rows.add(new AchievementView(
                    achievement,
                    progressOf(achievement.kind(), counters, level),
                    unlocked.contains(achievement.code())));
        }
        return new Sheet(rows, port.activeTitle(characterId));
    }

    /**
     * Đeo danh hiệu; mã rỗng = bỏ danh hiệu.
     *
     * @return {@code false} nếu thành tựu đó chưa mở (hoặc không tồn tại)
     */
    public boolean wearTitle(long characterId, String achievementCode) {
        if (achievementCode.isEmpty()) {
            port.clearActiveTitle(characterId);
            return true;
        }
        // Mã từ client: chặn hình dạng lạ (NUL làm Postgres nổ 500 — fuzz gateway bắt được) trước khi chạm DB.
        if (!achievementCode.matches("[a-z0-9_]{1,32}")) {
            return false;
        }
        return port.setActiveTitle(characterId, achievementCode);
    }

    /** Mã thành tựu có danh hiệu đang đeo, rỗng nếu không đeo gì. */
    public Optional<String> activeTitleCode(long characterId) {
        return port.activeTitle(characterId);
    }

    /** Khoá i18n danh hiệu đang đeo, rỗng nếu không đeo gì. */
    public Optional<String> activeTitleKey(long characterId) {
        Optional<String> code = port.activeTitle(characterId);
        if (code.isEmpty()) {
            return Optional.empty();
        }
        return port.definitions().stream()
                .filter(achievement -> achievement.code().equals(code.get()))
                .map(Achievement::titleKey)
                .findFirst();
    }

    private static long progressOf(AchievementKind kind, Map<AchievementKind, Long> counters, int level) {
        return kind.isCounted() ? counters.getOrDefault(kind, 0L) : level;
    }
}
