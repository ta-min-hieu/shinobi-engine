// Quản lý transaction tường minh (không @Transactional — ADR-009, DATABASE.md §5).
//
// ⚠️ PHÁT HIỆN THẬT: docs/templates/feature-module/domain/TransactionManager.java đặt tên có
// hậu tố "Manager" — chính Checkstyle của dự án (config/checkstyle/checkstyle.xml, rule cấm hậu
// tố Manager/Helper/Util/Info/Data) chặn tên đó khi biên dịch thật. Đặt tên "TransactionRunner"
// ở đây để qua được rule, giữ nguyên trách nhiệm/interface y hệt template. Cân nhắc cập nhật lại
// tên trong template cho khớp khi có feature tiếp theo copy template này.
package dev.kage.account.domain;

import java.util.function.Consumer;

/**
 * Chạy một khối lệnh trong transaction: mở → chạy → commit; exception → rollback.
 * Functional interface để test dùng Fake một dòng: {@code action -> action.accept(null)}.
 */
@FunctionalInterface
public interface TransactionRunner {
    void inTransaction(Consumer<Transaction> action);
}
