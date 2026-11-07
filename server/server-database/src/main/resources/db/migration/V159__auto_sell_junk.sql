-- ADR-204 — lọc nhặt đồ: bật thì đồ rác (item_template.junk, V136) rơi ra được bán ngay theo giá thu của Tàng Bảo Các thay
-- vì chiếm ô túi. Mặc định TẮT — đồ rác còn là vật nộp nhiệm vụ.
ALTER TABLE character ADD COLUMN auto_sell_junk BOOLEAN NOT NULL DEFAULT FALSE;
