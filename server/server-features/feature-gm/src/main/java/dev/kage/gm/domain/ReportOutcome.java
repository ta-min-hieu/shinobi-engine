package dev.kage.gm.domain;

/** Kết quả gửi báo cáo (ADR-105). */
public enum ReportOutcome {
    OK,
    UNKNOWN_TARGET,
    SELF,
    /** Đã gửi đủ {@link PlayerReport#MAX_PER_DAY} báo cáo hôm nay. */
    DAILY_LIMIT,
    /** Đã báo cáo đúng người này hôm nay. */
    ALREADY_REPORTED
}
