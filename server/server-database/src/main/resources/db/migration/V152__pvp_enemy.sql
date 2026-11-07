-- ADR-194 — danh sách cừu nhân: ai đã hạ mình trong dải PvP tự do của world chính. Giữ 20 người gần nhất mỗi nhân vật
-- (cắt ngay lúc ghi). Người chơi tự xoá từng dòng.
CREATE TABLE pvp_enemy (
    character_id        BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    enemy_character_id  BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    kill_count          INT         NOT NULL CHECK (kill_count > 0),
    last_killed_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, enemy_character_id),
    CHECK (character_id <> enemy_character_id)
);
