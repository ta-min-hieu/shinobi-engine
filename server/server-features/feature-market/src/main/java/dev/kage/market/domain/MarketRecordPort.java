package dev.kage.market.domain;

/** Khoá chống trùng (ADR-025) + audit, trong cùng transaction. */
public interface MarketRecordPort {

    /** Ghi khoá; đã dùng ⇒ ném {@link DuplicateRequestException} (rollback trọn). */
    void recordKey(Transaction tx, String idempotencyKey);

    void audit(Transaction tx, long characterId, String action, String detailJson);

    /** Khoá đã dùng. */
    final class DuplicateRequestException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DuplicateRequestException(Throwable cause) {
            super("idempotency_key đã dùng", cause);
        }
    }
}
