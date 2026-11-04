-- ADR-173 (2026-11-04) — Ghi chú bạn bè. Ghi chú RIÊNG của owner về một người bạn; người kia không thấy.
-- Hết là bạn thì dòng ở lại vô hại (chỉ đọc kèm danh sách bạn hiện tại) — kết bạn lại thì ghi chú cũ hiện lại.
CREATE TABLE friend_note (
    owner_id   BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    friend_id  BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    note       VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_id, friend_id)
);
