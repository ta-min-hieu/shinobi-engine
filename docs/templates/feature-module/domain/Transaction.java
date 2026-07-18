// TEMPLATE — Abstraction transaction. Domain thấy interface này, KHÔNG thấy java.sql.Connection.
// Implementation thật nằm ở server-database (bọc Connection + commit/rollback).
package dev.kage.inventory.domain;

/**
 * Handle của một transaction đang mở. Marker interface —
 * adapter ở infrastructure/ ép kiểu về implementation thật của server-database.
 */
public interface Transaction {}
