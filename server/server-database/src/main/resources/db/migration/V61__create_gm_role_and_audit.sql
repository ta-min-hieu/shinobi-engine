-- V61 — lệnh GM trong game (ADR-052, chủ dự án Accept 2026-10-18, lô 15). CHỈ THÊM bảng, không đổi bảng
-- đang chạy (DATABASE.md §10). Lùi bản = tắt tính năng (KAGE_GM_ENABLED=false) và để nguyên bảng —
-- KHÔNG BAO GIỜ DROP gm_audit_log (SECURITY.md §9: audit không được xoá).
--
-- Cấp/thu quyền CHỈ bằng SQL tay, cùng transaction với một dòng gm_audit_log (mẫu ở ADMIN.md):
--   BEGIN;
--   INSERT INTO gm_role (account_id, role, granted_at, note) VALUES (<id>, 'GM', now(), '<ai cấp, vì sao>');
--   INSERT INTO gm_audit_log (actor_account_id, actor_role, command, command_line, outcome, created_at, completed_at)
--        VALUES (<id người cấp>, 'ADMIN', 'role_grant', 'SQL tay: cấp GM cho <id>', 'OK', now(), now());
--   COMMIT;

CREATE TABLE gm_role (
    account_id  BIGINT PRIMARY KEY REFERENCES account (id),
    role        VARCHAR(8)   NOT NULL CHECK (role IN ('GM', 'ADMIN')),
    granted_at  TIMESTAMPTZ  NOT NULL,
    note        VARCHAR(200)
);

CREATE TABLE gm_audit_log (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_account_id     BIGINT       NOT NULL REFERENCES account (id),
    actor_character_id   BIGINT       REFERENCES character (id),  -- NULL chỉ cho dòng role_grant/role_revoke ghi tay
    actor_role           VARCHAR(8)   NOT NULL,                   -- role LÚC ra lệnh
    command              VARCHAR(16)  NOT NULL,
    command_line         VARCHAR(256) NOT NULL,                   -- nguyên văn dòng gõ
    target_character_id  BIGINT       REFERENCES character (id),
    outcome              VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                         CHECK (outcome IN ('PENDING', 'OK', 'REJECTED', 'DENIED', 'FAILED')),
    outcome_detail       TEXT,                                    -- server tự build, không phải input người dùng
    created_at           TIMESTAMPTZ  NOT NULL,
    completed_at         TIMESTAMPTZ
);
CREATE INDEX idx_gm_audit_log_actor ON gm_audit_log (actor_account_id, created_at);
CREATE INDEX idx_gm_audit_log_target ON gm_audit_log (target_character_id, created_at);

-- Thuộc feature-chat — tách bảng vì feature-chat không đọc gm_role/account.
CREATE TABLE chat_mute (
    account_id        BIGINT PRIMARY KEY REFERENCES account (id),
    muted_until       TIMESTAMPTZ NOT NULL,
    gm_audit_log_id   BIGINT      NOT NULL REFERENCES gm_audit_log (id)
);
