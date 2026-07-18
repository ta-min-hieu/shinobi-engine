// TEMPLATE — Quản lý transaction tường minh (không @Transactional — ADR-009).
package dev.kage.inventory.domain;

import java.util.function.Consumer;

/**
 * Chạy một khối lệnh trong transaction: mở → chạy → commit; exception → rollback.
 * Functional interface để test dùng Fake một dòng: {@code action -> action.accept(null)}.
 */
@FunctionalInterface
public interface TransactionManager {
    void inTransaction(Consumer<Transaction> action);
}
