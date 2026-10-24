package dev.kage.friend.domain;

/** Kết quả mọi lệnh đạo lữ (ADR-094). */
public enum DaoLuOutcome {
    OK,
    /** Chưa là bạn, hoặc thân mật dưới {@link DaoLuPolicy#MIN_INTIMACY}. */
    INTIMACY_TOO_LOW,
    /** Một trong hai đã có đạo lữ. */
    ALREADY_BOUND,
    /** Người cầu không đủ linh thạch. */
    INSUFFICIENT_GOLD,
    NO_PENDING_PROPOSAL,
    NOT_BOUND,
    CANNOT_TARGET_SELF
}
