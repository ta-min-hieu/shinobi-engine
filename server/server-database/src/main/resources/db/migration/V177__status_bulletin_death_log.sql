-- V177 — lô 51 (ADR-335, ADR-336, ADR-337).
-- ADR-335 lời nhắn trạng thái: một dòng mỗi nhân vật; không có dòng = không đặt.
CREATE TABLE status_message (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    message VARCHAR(60) NOT NULL
);

-- ADR-336 bảng rao vặt: mỗi nhân vật một tin, sống 24 giờ (lọc lúc đọc; dòng hết hạn bị thay ở lần đăng sau).
CREATE TABLE bulletin_post (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    body VARCHAR(100) NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_bulletin_post_posted_at ON bulletin_post (posted_at DESC);

-- ADR-337 lịch sử tử vong: audit 'DEATH' {killer_name, monster_code, x}.
CREATE INDEX idx_audit_log_death ON audit_log (character_id, id) WHERE action = 'DEATH';
