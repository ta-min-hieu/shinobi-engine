-- V3 — vị trí nhân vật trong world (SERVER.md §8: ghi ĐỊNH KỲ + lúc logout, KHÔNG mỗi tick —
-- mất vài phút vị trí chấp nhận được, mất item/gold thì không).
--
-- Mặc định (0,0) — nhân vật MỚI tạo (chưa từng có vị trí lưu) spawn ở gốc toạ độ, không cần
-- backfill gì thêm cho nhân vật đã tồn tại.
ALTER TABLE character
    ADD COLUMN pos_x REAL NOT NULL DEFAULT 0,
    ADD COLUMN pos_y REAL NOT NULL DEFAULT 0;
