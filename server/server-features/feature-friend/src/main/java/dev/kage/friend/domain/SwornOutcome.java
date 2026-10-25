package dev.kage.friend.domain;

/** Kết quả mời/nhận/rời kết nghĩa (ADR-110). */
public enum SwornOutcome {
    OK,
    TARGET_NOT_FOUND,
    CANNOT_TARGET_SELF,
    INTIMACY_TOO_LOW,
    /** Người được mời đã ở một nhóm kết nghĩa. */
    ALREADY_SWORN,
    /** Nhóm của người mời đã đủ {@link SwornPolicy#MAX_MEMBERS}. */
    GROUP_FULL,
    NO_PENDING_INVITE,
    INSUFFICIENT_GOLD,
    NOT_SWORN
}
