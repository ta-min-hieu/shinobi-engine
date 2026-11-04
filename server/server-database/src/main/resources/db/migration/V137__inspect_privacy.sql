-- ADR-171 (2026-11-04) — Soi trang bị người khác. Mặc định AI CŨNG xem được; một dòng ở đây = người chơi tắt cho xem.
CREATE TABLE character_inspect_privacy (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id),
    hidden       BOOLEAN     NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
