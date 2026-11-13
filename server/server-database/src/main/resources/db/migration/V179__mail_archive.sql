-- V179 — lô 53 (ADR-354). Thư lưu trữ: NULL = ở hộp chính.
ALTER TABLE mail ADD COLUMN archived_at TIMESTAMPTZ;
CREATE INDEX idx_mail_archived ON mail (recipient_id) WHERE archived_at IS NOT NULL;
