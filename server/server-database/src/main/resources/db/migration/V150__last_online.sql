-- ADR-192 — lần cuối online: mốc rời world gần nhất (không bị xoá khi vào lại, khác last_logout_at của bế quan).
ALTER TABLE character ADD COLUMN last_online_at TIMESTAMPTZ;

-- Người đang offline có sẵn mốc thoát của bế quan — mượn làm giá trị đầu.
UPDATE character SET last_online_at = last_logout_at WHERE last_logout_at IS NOT NULL;
