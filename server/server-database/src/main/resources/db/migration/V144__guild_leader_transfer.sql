-- ADR-183 (2026-11-07) — Nhường chức bang chủ. Không cột mới: đổi guild_member.role + guild.leader_character_id trong
-- một transaction. Chỉ mở rộng loại dòng nhật ký bang (ADR-180) thêm LEADER_CHANGED.
ALTER TABLE guild_log DROP CONSTRAINT guild_log_kind_check;
ALTER TABLE guild_log ADD CONSTRAINT guild_log_kind_check CHECK (kind IN ('CREATED', 'JOINED', 'LEFT', 'KICKED',
    'DONATED', 'LEVEL_UP', 'RENAMED', 'NOTICE_CHANGED', 'LEADER_CHANGED'));
