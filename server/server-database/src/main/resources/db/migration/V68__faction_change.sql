-- ADR-066 — đổi linh căn. Lần chọn đầu (thức tỉnh Lv10) miễn phí, lần thứ hai miễn phí nếu dưới Lv30,
-- sau đó mỗi lần tốn một Tẩy Căn Đan (GDD §4). Nhân vật đã đổi trước migration này đếm từ 0 — họ được
-- hưởng lại luật mới từ đầu, không ai bị phạt vì lần đổi khi luật chưa có.
ALTER TABLE character ADD COLUMN faction_changes INT NOT NULL DEFAULT 0 CHECK (faction_changes >= 0);

INSERT INTO item_template (code, name_key, max_stack)
SELECT 'faction_change_pill', 'item.faction_change_pill.name', 99
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'faction_change_pill');

-- Tham số (Điều 27): đắt có chủ đích — đổi linh căn là quyết định lớn, không phải nút thử chiêu.
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 20000, 1000 FROM item_template WHERE code = 'faction_change_pill';
