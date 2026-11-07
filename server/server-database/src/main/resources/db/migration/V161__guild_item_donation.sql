-- ADR-207 (2026-11-07) — góp vật phẩm cho bang: mỗi lần góp là một dòng nhật ký bang
-- (amount = số lượng, detail = mã vật phẩm). Không bảng mới: cống hiến + kinh nghiệm bang dùng cột sẵn có.
ALTER TABLE guild_log DROP CONSTRAINT guild_log_kind_check;
ALTER TABLE guild_log ADD CONSTRAINT guild_log_kind_check CHECK (kind IN ('CREATED', 'JOINED', 'LEFT', 'KICKED',
    'DONATED', 'LEVEL_UP', 'RENAMED', 'NOTICE_CHANGED', 'LEADER_CHANGED', 'VICE_APPOINTED', 'VICE_DISMISSED',
    'BANK_DEPOSIT', 'BANK_WITHDRAW', 'ITEM_DONATED'));
