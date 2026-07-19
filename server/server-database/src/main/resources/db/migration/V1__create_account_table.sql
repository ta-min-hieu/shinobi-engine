-- V1 — bảng account tối thiểu cho đăng nhập username-only (dự án cá nhân, chưa cần mật khẩu).
-- DATABASE.md §2.1 mô tả schema đầy đủ (password_hash, email, status ACTIVE/BANNED/SUSPENDED) —
-- password_hash/email THÊM SAU khi có đăng nhập thật (Điều 40: cột mới luôn nullable trước,
-- backfill, rồi mới NOT NULL — không migrate trước khi cần, tránh đổi schema 2 lần).
-- status có ngay từ V1 vì đây là lý do cốt lõi ADR-017 chọn session token (ban tức thì).
CREATE TABLE account (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL
);
