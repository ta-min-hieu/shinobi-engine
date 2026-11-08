-- ADR-217 (2026-11-08) — ghim thư: thư ghim nằm đầu hộp thư và không bị xoá hàng loạt (ADR-216).
ALTER TABLE mail ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

-- ADR-218 (2026-11-08) — hồ sơ cá nhân: một dòng giới thiệu tự viết, hiện khi người khác soi trang bị (ADR-171).
ALTER TABLE character ADD COLUMN bio VARCHAR(60) NOT NULL DEFAULT '';
