package dev.kage.gm.domain;

/** Kết quả đổi mã quà (ADR-104). */
public enum GiftCodeOutcome {
    OK,
    UNKNOWN_CODE,
    EXPIRED,
    USED_UP,
    ALREADY_REDEEMED
}
