package dev.kage.crafting.domain;

import java.util.function.Consumer;

/** Chạy một khối lệnh trong transaction: exception ⇒ rollback. Test dùng {@code action -> action.accept(null)}. */
@FunctionalInterface
public interface TransactionRunner {
    void inTransaction(Consumer<Transaction> action);
}
