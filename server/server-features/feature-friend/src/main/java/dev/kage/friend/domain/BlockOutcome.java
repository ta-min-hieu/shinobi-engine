package dev.kage.friend.domain;

/** Kết quả chặn / bỏ chặn (ADR-091). */
public enum BlockOutcome {
    OK,
    CANNOT_BLOCK_SELF,
    ALREADY_BLOCKED,
    /** Đã chặn đủ {@link BlockPolicy#MAX_BLOCKED} người. */
    LIST_FULL,
    /** Bỏ chặn một người chưa từng bị chặn. */
    NOT_BLOCKED
}
