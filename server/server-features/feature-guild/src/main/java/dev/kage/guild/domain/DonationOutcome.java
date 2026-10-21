package dev.kage.guild.domain;

/** Kết quả một lần góp linh thạch cho bang (ADR-068 D2). */
public enum DonationOutcome {
    OK,
    NOT_IN_GUILD,
    INSUFFICIENT_GOLD,
    INVALID_AMOUNT,
    /** Khoá chống trùng đã dùng — lần gửi lại của cùng một lệnh, không trừ gì thêm. */
    DUPLICATE_REQUEST
}
