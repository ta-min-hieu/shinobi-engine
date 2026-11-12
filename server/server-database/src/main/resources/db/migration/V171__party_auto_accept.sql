-- ADR-286 (2026-11-12) — tự nhận lời mời nhóm từ bạn bè: bật thì server tự chấp nhận ngay khi một người BẠN mời.
ALTER TABLE character ADD COLUMN auto_accept_friend_party BOOLEAN NOT NULL DEFAULT FALSE;
