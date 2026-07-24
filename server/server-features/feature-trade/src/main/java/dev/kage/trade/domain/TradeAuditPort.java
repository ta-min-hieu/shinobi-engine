package dev.kage.trade.domain;

/** Ghi {@code audit_log} — bắt buộc cho MỌI giao dịch trade thi hành (GAMEPLAY.md §8 "điều tra gian lận"). */
public interface TradeAuditPort {
    void log(Transaction tx, long characterId, String action, String detailJson);
}
