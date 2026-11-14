-- ADR-383 (lô 57) — chế độ ẩn: bạn bè thấy nhân vật như đang offline. Có dòng = đang ẩn.
CREATE TABLE invisible_mode (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    since TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ADR-386 — dọn thư cũ đã đọc: lọc theo read_at.
CREATE INDEX idx_mail_read_at ON mail (read_at) WHERE read_at IS NOT NULL;
