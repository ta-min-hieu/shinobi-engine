-- V2 — bảng character/character_stat (DATABASE.md §2.1) + idempotency_key (ADR-025, dùng chung
-- mọi feature có Command side-effect, không riêng character).
--
-- character_stat tách khỏi character vì ghi/đọc tần suất cao hơn nhiều (DATABASE.md §2.1). Số
-- liệu khởi điểm str/agi/int_/vit = 10 là PLACEHOLDER — GDD chưa chốt số liệu khởi điểm thật
-- (feature-character/CLAUDE.md), sửa lại khi GDD có số liệu chính thức.
--
-- realm_id mặc định 1 (ADR-010 — single-realm MVP, cột có sẵn cho multi-realm sau, không migrate
-- lại). faction lưu dạng tên (VARCHAR) để dễ đọc khi debug — khớp phong cách cột "status" ở V1.
CREATE TABLE character (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES account (id),
    realm_id INT NOT NULL DEFAULT 1,
    name VARCHAR(16) NOT NULL UNIQUE,
    faction VARCHAR(16) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    exp BIGINT NOT NULL DEFAULT 0,
    rebirth_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_character_account_id ON character (account_id);

CREATE TABLE character_stat (
    character_id BIGINT PRIMARY KEY REFERENCES character (id),
    str INT NOT NULL,
    agi INT NOT NULL,
    int_ INT NOT NULL,
    vit INT NOT NULL,
    current_hp INT NOT NULL,
    current_chi INT NOT NULL
);

-- ADR-025: idempotency key cho Command có side-effect. Ghi trong CÙNG transaction với hiệu ứng
-- thật — vi phạm PRIMARY KEY (key đã dùng) → transaction rollback, KHÔNG lặp lại hiệu ứng.
CREATE TABLE idempotency_key (
    key UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);
