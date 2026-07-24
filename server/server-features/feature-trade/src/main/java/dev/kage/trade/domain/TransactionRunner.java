package dev.kage.trade.domain;

import java.util.function.Consumer;

/**
 * Chạy một khối lệnh trong transaction: mở → chạy → commit; exception → rollback.
 * Functional interface để test dùng Fake một dòng: {@code action -> action.accept(null)}.
 */
@FunctionalInterface
public interface TransactionRunner {
    void inTransaction(Consumer<Transaction> action);
}
