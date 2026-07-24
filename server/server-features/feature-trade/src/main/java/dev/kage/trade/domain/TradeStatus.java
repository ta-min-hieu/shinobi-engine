package dev.kage.trade.domain;

/** Chỉ 2 trạng thái LƯU LẠI — EXECUTED/CANCELLED không lưu, xoá dòng khi xong (xem migration V16). */
public enum TradeStatus {
    PENDING_ACCEPT,
    OPEN
}
