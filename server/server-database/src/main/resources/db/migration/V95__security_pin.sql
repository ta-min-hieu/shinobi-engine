-- V95 — khoá an toàn: PIN cấp 2 của tài khoản (ADR-115, lô 23).
-- Chỉ băm PBKDF2 + muối (hex), KHÔNG bao giờ lưu PIN thô. locked_until NULL = không bị khoá.
CREATE TABLE account_security_pin (
    account_id      BIGINT PRIMARY KEY REFERENCES account (id),
    pin_hash        VARCHAR(128) NOT NULL,
    pin_salt        VARCHAR(64)  NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL
);
