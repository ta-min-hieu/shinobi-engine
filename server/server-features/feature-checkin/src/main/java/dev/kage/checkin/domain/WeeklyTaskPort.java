package dev.kage.checkin.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nhiệm vụ tuần cá nhân (ADR-154, V125) — hiện thực ở server-database. Đếm là một UPSERT cho mọi việc khớp mã hoạt
 * động; nhận thưởng là MỘT giao dịch dưới khoá túi.
 */
public interface WeeklyTaskPort {

    /** Kết quả nhận thưởng. */
    enum Outcome {
        OK,
        UNKNOWN_TASK,
        NOT_DONE,
        ALREADY_CLAIMED,
        BAG_FULL,
        GOLD_CAP
    }

    /**
     * Một việc và tiến độ tuần này của nhân vật.
     *
     * @param itemTemplateId 0 nếu chỉ thưởng linh thạch
     */
    record Task(
            String code,
            String nameKey,
            int target,
            int progress,
            boolean claimed,
            long gold,
            long itemTemplateId,
            int quantity) {}

    /** Mọi việc theo thứ tự hiển thị, tiến độ của tuần {@code weekStart}. */
    List<Task> tasks(long characterId, LocalDate weekStart);

    /** +1 cho mọi việc có {@code activity_code = activityCode} (không có việc nào khớp thì không ghi gì). */
    void record(long characterId, LocalDate weekStart, String activityCode);

    /** Nhận thưởng việc {@code taskCode} của tuần {@code weekStart}; từ chối thì rollback trọn. */
    Outcome claim(long characterId, LocalDate weekStart, String taskCode, Instant now);
}
