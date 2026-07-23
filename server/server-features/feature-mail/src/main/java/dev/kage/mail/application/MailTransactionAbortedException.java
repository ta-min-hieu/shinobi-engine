package dev.kage.mail.application;

/**
 * Rollback-signal nội bộ — ném khi cấp item đính kèm thất bại (túi đầy) giữa lúc đang nhận thư, để
 * {@code TransactionRunner} rollback CẢ gold đã cộng + trạng thái claimed (KHÔNG được "nhận gold
 * nhưng mất item", DATABASE.md §5) — cùng mẫu {@code ShopTransactionAbortedException}.
 */
final class MailTransactionAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
}
