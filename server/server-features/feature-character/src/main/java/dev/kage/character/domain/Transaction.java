// Abstraction transaction. Domain thấy interface này, KHÔNG thấy java.sql.Connection.
// Implementation thật nằm ở server-database (bọc Connection + commit/rollback) — cùng quy ước
// docs/templates/feature-module/domain/Transaction.java và feature-account/domain/Transaction.java.
package dev.kage.character.domain;

/**
 * Handle của một transaction đang mở. Marker interface —
 * adapter ở server-database ép kiểu về implementation thật.
 */
public interface Transaction {
}
