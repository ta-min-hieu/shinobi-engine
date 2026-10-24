package dev.kage.friend.domain;

/** Kết quả mọi lệnh sư đồ (ADR-093). */
public enum MentorshipOutcome {
    OK,
    DISCIPLE_LEVEL_TOO_HIGH,
    MENTOR_LEVEL_TOO_LOW,
    ALREADY_HAS_MENTOR,
    MENTOR_FULL,
    /** Không có lời bái sư đang chờ (hoặc đã quá hạn). */
    NO_PENDING_REQUEST,
    /** Hai người không phải sư đồ. */
    NOT_RELATED,
    CANNOT_TARGET_SELF
}
