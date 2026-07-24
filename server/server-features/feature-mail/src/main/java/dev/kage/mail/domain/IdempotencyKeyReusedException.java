package dev.kage.mail.domain;

/** Ném khi adapter phát hiện vi phạm UNIQUE {@code idempotency_key.key} (ADR-025). */
public final class IdempotencyKeyReusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("idempotencyKey đã dùng trước đó: " + idempotencyKey);
    }
}
