package dev.kage.quest.domain;

/**
 * ADR-025 — chống double-effect khi client tự retry {@code ClaimQuestRewardCommand} (mất kết nối
 * trước khi nhận response, không biết server đã xử lý hay chưa). Ghi trong CÙNG transaction với
 * hiệu ứng thật (EXP + item + đổi status CLAIMED) — vi phạm UNIQUE (key đã dùng) → transaction
 * rollback, KHÔNG lặp lại hiệu ứng. Dùng chung bảng {@code idempotency_key} (V2, đã tạo sẵn "dùng
 * chung mọi feature có Command side-effect") — bản sao Port ý tưởng feature-shop/feature-mail/
 * feature-trade, KHÔNG phụ thuộc Gradle vào feature đó (mỗi feature tự khai báo Port/Transaction
 * riêng).
 */
public interface IdempotencyPort {

    /**
     * @throws IdempotencyKeyReusedException nếu {@code idempotencyKey} đã dùng trước đó
     */
    void recordOrThrow(Transaction tx, String idempotencyKey);
}
