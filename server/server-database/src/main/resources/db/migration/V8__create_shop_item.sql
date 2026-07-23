-- V8 — bảng shop_item (danh mục mua/bán, GAMEPLAY.md §8 / feature-shop/CLAUDE.md). Phase 5.
--
-- 1 dòng = 1 item template bán được ở shop toàn cục (chưa gắn NPC/vị trí — Phase 6 mới có NPC).
-- buy_price/sell_price ĐỘC LẬP nhau (không phải % cố định của nhau) — chủ dự án chọn tự cân bằng
-- từng item thay vì áp 1 công thức chung.
CREATE TABLE shop_item (
    item_template_id BIGINT PRIMARY KEY REFERENCES item_template (id),
    buy_price BIGINT NOT NULL CHECK (buy_price >= 0),
    sell_price BIGINT NOT NULL CHECK (sell_price >= 0)
);

-- Seed giá PLACEHOLDER (GDD chưa chốt cân bằng kinh tế, cùng tinh thần STARTING_STAT/DEFAULT_CAPACITY
-- đã ghi ở PostgresCharacterAdapter/PostgresInventoryAdapter) — kunai tiêu hao mua rẻ bán rẻ, wooden_sword
-- trang bị mua đắt hơn hẳn.
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 5, 2 FROM item_template WHERE code = 'kunai';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 100, 40 FROM item_template WHERE code = 'wooden_sword';
