// Quản lý transaction tường minh (không @Transactional — ADR-009, DATABASE.md §5).
// Tên "TransactionRunner" chứ không phải "TransactionManager" — Checkstyle dự án cấm hậu tố
// Manager/Helper/Util/Info/Data (xem feature-account/CLAUDE.md mục "Khác template chuẩn").
package dev.kage.character.domain;

import java.util.function.Consumer;

/**
 * Chạy một khối lệnh trong transaction: mở → chạy → commit; exception → rollback.
 * Functional interface để test dùng Fake một dòng: {@code action -> action.accept(null)}.
 */
@FunctionalInterface
public interface TransactionRunner {
    void inTransaction(Consumer<Transaction> action);
}
