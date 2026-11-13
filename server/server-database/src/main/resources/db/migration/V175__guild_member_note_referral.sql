-- V175 — lô 49 (ADR-319, ADR-320, ADR-322).
-- ADR-319 ghi chú thành viên bang: chỉ bang chủ / phó đọc và ghi; rỗng = chưa ghi.
ALTER TABLE guild_member ADD COLUMN officer_note VARCHAR(100) NOT NULL DEFAULT '';

-- ADR-320 mã giới thiệu: mỗi nhân vật được giới thiệu MỘT lần; rewarded_at đặt khi người mới lên cấp 10 (cả hai nhận quà).
CREATE TABLE referral (
    referred_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    referrer_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    rewarded_at TIMESTAMPTZ,
    CHECK (referred_id <> referrer_id)
);
CREATE INDEX idx_referral_referrer ON referral (referrer_id);

-- ADR-322 lịch sử đột phá: audit 'BREAKTHROUGH' {realm, level}.
CREATE INDEX idx_audit_log_breakthrough ON audit_log (character_id, id) WHERE action = 'BREAKTHROUGH';
