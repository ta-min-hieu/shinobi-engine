-- ADR-236 (2026-11-08) — không làm phiền: chặn tin riêng + lời mời (nhóm/giao dịch/kết bạn) từ người không phải bạn.
ALTER TABLE character ADD COLUMN do_not_disturb BOOLEAN NOT NULL DEFAULT FALSE;

-- ADR-237 (2026-11-08) — chặn lời mời nhóm/giao dịch từ người không phải bạn, không cùng bang.
ALTER TABLE character ADD COLUMN block_stranger_invites BOOLEAN NOT NULL DEFAULT FALSE;

-- ADR-238 (2026-11-08) — lịch sử đăng nhập theo tài khoản. CHỈ lưu IP đã che (không bao giờ IP đầy đủ); mỗi tài khoản
-- giữ 50 dòng mới nhất (cắt lúc ghi).
CREATE TABLE login_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    masked_ip  VARCHAR(48) NOT NULL,
    logged_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_login_history_account ON login_history (account_id, id DESC);

-- ADR-235 (2026-11-08) — trần thư toàn bang mỗi ngày đếm trên audit GUILD_MAIL.
CREATE INDEX idx_audit_log_guild_mail ON audit_log (created_at DESC) WHERE action = 'GUILD_MAIL';
