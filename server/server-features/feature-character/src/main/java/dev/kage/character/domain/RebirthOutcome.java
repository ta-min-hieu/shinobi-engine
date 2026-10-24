package dev.kage.character.domain;

/** Kết quả Độ Kiếp Chuyển Thế (ADR-089). */
public enum RebirthOutcome {
    OK,
    /** Chưa tới cấp tối đa. */
    LEVEL_TOO_LOW,
    /** Đã chuyển thế đủ {@link RebirthPolicy#MAX_REBIRTHS} lần. */
    MAX_REBIRTHS,
    NOT_FOUND
}
