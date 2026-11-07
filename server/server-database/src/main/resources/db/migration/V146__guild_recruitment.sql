-- ADR-185 (2026-11-07) — Bảng chiêu mộ bang. Mỗi bang tối đa một dòng (bang chủ / phó treo); xoá dòng = gỡ chiêu mộ.
-- min_level được KIỂM ở đơn xin vào (server), không chỉ hiển thị.
CREATE TABLE guild_recruitment (
    guild_id   BIGINT      PRIMARY KEY REFERENCES guild (id) ON DELETE CASCADE,
    min_level  INT         NOT NULL CHECK (min_level BETWEEN 1 AND 100),
    message    VARCHAR(160) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
