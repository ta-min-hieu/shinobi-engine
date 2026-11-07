package dev.kage.achievement.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lưu trữ thành tựu (ADR-062) — hiện thực ở server-database. */
public interface AchievementPort {

    /** Mọi thành tựu, theo thứ tự hiển thị. */
    List<Achievement> definitions();

    /** Cộng {@code delta} vào bộ đếm và trả giá trị MỚI (UPSERT nguyên tử). */
    long addToCounter(long characterId, AchievementKind kind, long delta);

    /** Mọi bộ đếm hiện có của nhân vật (loại chưa có dòng nào thì vắng mặt = 0). */
    Map<AchievementKind, Long> counters(long characterId);

    /**
     * Ghi đã mở.
     *
     * @return {@code true} CHỈ nếu lần này mới mở — lượt khác mở trước thì {@code false} (D4: không báo đôi)
     */
    boolean unlock(long characterId, String achievementCode);

    Set<String> unlocked(long characterId);

    /** ADR-209 — số thành tựu đã mở của mọi nhân vật có ít nhất một: {@code characterId → số}. */
    Map<Long, Integer> unlockedCounts();

    /** Thành tựu có danh hiệu đang đeo, rỗng nếu không đeo gì. */
    Optional<String> activeTitle(long characterId);

    /**
     * Đeo danh hiệu của {@code achievementCode} — CHỈ khi đã mở (kiểm trong cùng câu lệnh).
     *
     * @return {@code false} nếu chưa mở
     */
    boolean setActiveTitle(long characterId, String achievementCode);

    void clearActiveTitle(long characterId);
}
