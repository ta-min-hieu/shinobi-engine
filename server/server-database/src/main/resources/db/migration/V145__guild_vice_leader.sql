-- ADR-184 (2026-11-07) — Phó bang chủ. guild_member.role nay nhận thêm 'VICE_LEADER' (cột VARCHAR(16), không có CHECK
-- nên không đổi cột). Chỉ mở rộng loại dòng nhật ký bang: VICE_APPOINTED / VICE_DISMISSED.
ALTER TABLE guild_log DROP CONSTRAINT guild_log_kind_check;
ALTER TABLE guild_log ADD CONSTRAINT guild_log_kind_check CHECK (kind IN ('CREATED', 'JOINED', 'LEFT', 'KICKED',
    'DONATED', 'LEVEL_UP', 'RENAMED', 'NOTICE_CHANGED', 'LEADER_CHANGED', 'VICE_APPOINTED', 'VICE_DISMISSED'));
