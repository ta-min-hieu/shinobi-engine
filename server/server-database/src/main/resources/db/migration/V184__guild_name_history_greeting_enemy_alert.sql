-- ADR-423 (lô 62) — lịch sử tên bang: mỗi lần đổi tên thành công một dòng (ghi từ lô 62 trở đi).
CREATE TABLE guild_name_history (
    guild_id   BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    old_name   VARCHAR(32) NOT NULL,
    new_name   VARCHAR(32) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guild_name_history_guild ON guild_name_history (guild_id, changed_at);
CREATE INDEX idx_guild_name_history_old ON guild_name_history (lower(old_name));

-- ADR-425 — câu chào khi vào game (bạn bè đang online thấy). Không có dòng = không chào.
CREATE TABLE login_greeting (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    message      VARCHAR(60) NOT NULL
);

-- ADR-426 — tắt báo cừu nhân online. Có dòng = đã tắt (mặc định bật).
CREATE TABLE enemy_alert_off (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE
);
