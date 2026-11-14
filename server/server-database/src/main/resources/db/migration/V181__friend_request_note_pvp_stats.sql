-- V181 — lô 56 (ADR-376, ADR-378).
-- ADR-376 lời nhắn kèm lời mời kết bạn: một dòng mỗi cặp (người mời, người nhận); lời mời mới ghi đè.
CREATE TABLE friend_request_note (
    requester_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    addressee_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    message VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (requester_id, addressee_id)
);

-- ADR-378 thống kê PvP ngoài đấu trường.
ALTER TABLE character_lifetime_stats ADD COLUMN player_kills INT NOT NULL DEFAULT 0;
ALTER TABLE character_lifetime_stats ADD COLUMN deaths_by_player INT NOT NULL DEFAULT 0;
