package dev.kage.trade.domain;

/**
 * ADR-025 — chống double-effect khi client tự retry {@code ConfirmTradeCommand} (mất kết nối
 * trước khi nhận response, không biết server đã xử lý/thi hành hay chưa). Ghi trong CÙNG
 * transaction với hiệu ứng thật (chuyển gold/item) — vi phạm UNIQUE (key đã dùng) → transaction
 * rollback, KHÔNG lặp lại hiệu ứng. Dùng chung bảng {@code idempotency_key} (V2) — bản sao Port ý
 * tưởng feature-character/feature-shop/feature-mail.
 */
public interface IdempotencyPort {

    /**
     * Ghi nhận {@code idempotencyKey} trong transaction {@code tx}; key trùng thì ném lỗi.
     *
     * @throws IdempotencyKeyReusedException nếu {@code idempotencyKey} đã dùng trước đó
     */
    void recordOrThrow(Transaction tx, String idempotencyKey);
}
