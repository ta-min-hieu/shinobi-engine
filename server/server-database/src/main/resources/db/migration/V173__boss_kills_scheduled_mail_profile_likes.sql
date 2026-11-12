-- V173 — lô 46 (ADR-296, ADR-297, ADR-298).
-- ADR-296 lịch sử boss đã hạ: audit 'BOSS_KILL' {boss, kind, rank, damage}; chỉ mục một phần để đọc 20 dòng mới nhất.
CREATE INDEX idx_audit_log_boss_kill ON audit_log (character_id, id DESC) WHERE action = 'BOSS_KILL';

-- ADR-297 thư hẹn giờ: server giữ thư thường (không đính kèm, không vàng) tới send_at rồi gửi qua đường gửi thư thường.
CREATE TABLE scheduled_mail (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    subject VARCHAR(64) NOT NULL,
    body VARCHAR(1024) NOT NULL,
    send_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scheduled_mail_due ON scheduled_mail (send_at);
CREATE INDEX idx_scheduled_mail_sender ON scheduled_mail (sender_id);

-- ADR-298 thích hồ sơ: mỗi người thích một hồ sơ tối đa một lần mỗi ngày (giờ server, UTC).
CREATE TABLE profile_like (
    liker_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    target_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    liked_on DATE NOT NULL,
    PRIMARY KEY (liker_id, target_id, liked_on),
    CHECK (liker_id <> target_id)
);
CREATE INDEX idx_profile_like_target ON profile_like (target_id);
