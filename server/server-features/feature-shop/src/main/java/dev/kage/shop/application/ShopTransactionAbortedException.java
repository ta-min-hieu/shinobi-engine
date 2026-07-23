package dev.kage.shop.application;

/**
 * Ném ra để buộc {@code TransactionRunner} rollback khi 1 bước ĐÃ có hiệu lực (VD đã trừ gold)
 * nhưng bước SAU thất bại (VD túi đầy) — {@code JdbcTransactionRunner} chỉ rollback khi action
 * ném exception, return thường sẽ commit. Package-private: chỉ dùng nội bộ trong application/.
 */
final class ShopTransactionAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
}
