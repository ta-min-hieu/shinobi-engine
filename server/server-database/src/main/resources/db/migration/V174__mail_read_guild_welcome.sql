-- V174 — lô 48 (ADR-312, ADR-313, ADR-314).
-- ADR-314 thư chưa đọc: NULL = chưa đọc. Thư có từ trước coi như đã đọc (người chơi đã thấy chúng trước khi có tính năng).
ALTER TABLE mail ADD COLUMN read_at TIMESTAMPTZ;
UPDATE mail SET read_at = created_at;
CREATE INDEX idx_mail_unread ON mail (recipient_id) WHERE read_at IS NULL;
-- ADR-313 lời chào thành viên mới: rỗng = không chào.
ALTER TABLE guild ADD COLUMN welcome_message VARCHAR(200) NOT NULL DEFAULT '';
-- ADR-312 thư gửi mọi bạn bè: giới hạn mỗi ngày đếm trên audit 'FRIEND_MAIL'.
CREATE INDEX idx_audit_log_friend_mail ON audit_log (character_id, created_at DESC) WHERE action = 'FRIEND_MAIL';
