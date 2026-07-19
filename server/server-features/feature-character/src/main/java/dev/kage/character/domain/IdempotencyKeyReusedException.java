package dev.kage.character.domain;

/**
 * {@code idempotencyKey} đã dùng trước đó (ADR-025) — Command side-effect KHÔNG được thực hiện
 * lại lần 2. Adapter ném lỗi này khi phát hiện vi phạm unique constraint trên
 * {@code idempotency_key}, dịch từ chi tiết SQL sang tín hiệu domain-agnostic.
 */
public final class IdempotencyKeyReusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("idempotencyKey đã được dùng trước đó: " + idempotencyKey);
    }
}
