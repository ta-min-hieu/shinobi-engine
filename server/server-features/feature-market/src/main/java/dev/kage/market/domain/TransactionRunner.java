package dev.kage.market.domain;

import java.util.function.Consumer;

/** Chạy {@code action} trong MỘT transaction; ném ⇒ rollback. */
@FunctionalInterface
public interface TransactionRunner {
    void inTransaction(Consumer<Transaction> action);
}
