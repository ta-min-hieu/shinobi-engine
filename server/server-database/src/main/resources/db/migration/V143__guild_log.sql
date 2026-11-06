-- ADR-180 (2026-11-06) — Nhật ký bang. Mỗi dòng ghi CÙNG transaction với việc nó kể (vào/rời/bị đuổi bang, góp bang,
-- bang lên cấp, đổi tên, đổi thông báo) — không có dòng nhật ký nào cho việc đã rollback. Giữ 90 ngày (RetentionJanitor).
CREATE TABLE guild_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id   BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    kind       VARCHAR(16) NOT NULL CHECK (kind IN ('CREATED', 'JOINED', 'LEFT', 'KICKED', 'DONATED', 'LEVEL_UP',
                                                    'RENAMED', 'NOTICE_CHANGED')),
    actor_id   BIGINT      REFERENCES character (id) ON DELETE SET NULL,
    subject_id BIGINT      REFERENCES character (id) ON DELETE SET NULL,
    amount     BIGINT      NOT NULL DEFAULT 0,
    detail     VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guild_log_guild ON guild_log (guild_id, created_at DESC, id DESC);
CREATE INDEX idx_guild_log_created ON guild_log (created_at);
