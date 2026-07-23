-- V12 — bạn bè (GAMEPLAY.md §11, Phase 6 lát cắt 4). Trạng thái online KHÔNG lưu ở DB — dùng
-- registry in-memory sẵn có ở server-gateway (ConnectedCharacterRegistry, ADR-010 single-instance
-- nên chưa cần Redis presence riêng, xem feature-friend/CLAUDE.md).
--
-- 1 dòng = 1 lời mời kết bạn, hướng requester -> addressee. Sau khi ACCEPTED, dòng vẫn giữ nguyên
-- (không tạo dòng ngược lại) — đọc quan hệ bạn bè của 1 nhân vật phải query CẢ 2 cột
-- (requester_id = ? OR addressee_id = ?) AND status = 'ACCEPTED'.
CREATE TABLE friendship (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES character (id),
    addressee_id BIGINT NOT NULL REFERENCES character (id),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CHECK (requester_id <> addressee_id),
    UNIQUE (requester_id, addressee_id)
);

CREATE INDEX idx_friendship_requester ON friendship (requester_id, status);
CREATE INDEX idx_friendship_addressee ON friendship (addressee_id, status);
