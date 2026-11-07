-- ADR-187 (2026-11-07) — Nhắn riêng khi offline. Tin nhắn riêng tới người đang offline được giữ ở đây (đã qua đúng
-- kiểm duyệt + audit của chat), giao khi người nhận vào game rồi xoá. Tối đa 20 tin chờ / người nhận, giữ 7 ngày.
CREATE TABLE offline_whisper (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    sender_id    BIGINT       NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    body         VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_offline_whisper_recipient ON offline_whisper (recipient_id, id);
CREATE INDEX idx_offline_whisper_created ON offline_whisper (created_at);
