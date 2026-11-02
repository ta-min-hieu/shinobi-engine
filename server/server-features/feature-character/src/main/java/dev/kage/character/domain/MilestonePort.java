package dev.kage.character.domain;

import java.time.Instant;
import java.util.List;

/**
 * Quà mốc cảnh giới (ADR-153, V124) — hiện thực ở server-database. Nhận quà là MỘT giao dịch dưới khoá túi của nhân
 * vật: đủ cấp, chưa nhận (PRIMARY KEY), trao quà, ghi lần nhận + audit.
 */
public interface MilestonePort {

    /** Kết quả nhận quà. */
    enum Outcome {
        OK,
        UNKNOWN_MILESTONE,
        LEVEL_TOO_LOW,
        ALREADY_CLAIMED,
        BAG_FULL,
        GOLD_CAP
    }

    /**
     * Một mốc và trạng thái của nhân vật.
     *
     * @param itemTemplateId 0 nếu mốc chỉ thưởng linh thạch
     */
    record Milestone(int level, String nameKey, long gold, long itemTemplateId, int quantity, boolean claimed) {}

    /** Mọi mốc theo cấp tăng dần + cấp hiện tại của nhân vật (chỉ đọc). */
    record Board(int characterLevel, List<Milestone> milestones) {

        public Board {
            milestones = List.copyOf(milestones);
        }
    }

    Board board(long characterId);

    /** Nhận quà mốc {@code level}; từ chối ở bước nào thì rollback trọn. */
    Outcome claim(long characterId, int level, Instant now);
}
