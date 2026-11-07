-- ADR-212 (2026-11-07) — chặn thư người lạ: bật thì chỉ bạn bè gửi thư cho mình được (thư hệ thống luôn tới).
ALTER TABLE character ADD COLUMN block_stranger_mail BOOLEAN NOT NULL DEFAULT FALSE;
