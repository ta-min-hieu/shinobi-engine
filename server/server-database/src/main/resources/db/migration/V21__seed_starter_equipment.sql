-- V21 — nội dung vật phẩm khởi điểm: phủ ĐỦ 7 ô trang bị + vòng kinh tế mua/bán/rơi đồ
-- (GAMEPLAY.md §7/§7.2, GDD §5 "Trang bị"). CHỈ INSERT dữ liệu, KHÔNG đổi schema.
--
-- Trước migration này cả game có 3 dòng item_template (kunai V4, wooden_sword V5,
-- enhance_protection_charm V9) — 6/7 ô trang bị KHÔNG có gì để mặc, cửa hàng có 3 dòng, quái chỉ
-- rơi kunai. Đây là nội dung, không phải cơ chế: mọi thứ dưới đây chạy được ngay bằng code đã có
-- (EquipItemUseCase / BuyItemUseCase / SellItemUseCase / GrantLootUseCase), không thêm 1 dòng Java.
--
-- ⚠️ HẠN CHẾ ĐÃ BIẾT — chỉ cộng ATK. item_template mới có DUY NHẤT cột chỉ số bonus_atk (V6);
-- GDD §5 còn DEF/RES/HP... nhưng thêm cột là ĐỔI SCHEMA, phải hỏi chủ dự án (/CLAUDE.md §4). Nên
-- giáp/mũ/găng/giày ở đây cộng ATK nhỏ thay vì DEF — nếu để 0 thì 6/7 ô trang bị vẫn vô nghĩa,
-- đúng cái mà migration này sinh ra để sửa. Khi có cột DEF thật thì chỉnh lại số ở ĐÂY, không cần
-- đổi code (cùng tinh thần "DB = tham số", Điều 27).
--
-- ⚠️ Hai bậc, KHÔNG hơn: "Tập Sự" (mua được ngay từ shop, cho người mới) và "Hạ Nhẫn" (đắt hơn
-- ~15 lần, vừa mua được vừa rơi từ boss — chỗ tiêu vàng đầu tiên của game, GDD §14 Gold Sink).
--
-- ⚠️ GIÁ: buy_price LUÔN > sell_price cho MỌI dòng — bằng hoặc thấp hơn là lỗ hổng đúc vàng vô hạn
-- (mua rẻ → bán lại lời). Giữ nguyên bất biến này khi chỉnh cân bằng sau này.
--
-- Tra item_template.id qua code (khuôn V9), KHÔNG hardcode ID số — ID phụ thuộc thứ tự seed.

-- ---------------------------------------------------------------------------------------------
-- 1. Trang bị bậc "Tập Sự" — 6 ô còn thiếu (ô WEAPON đã có wooden_sword từ V5).
--    max_stack = 1: trang bị không xếp chồng (khuôn wooden_sword).
-- ---------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk) VALUES
    ('novice_vest',      'item.novice_vest.name',      1, 'ARMOR',       2),
    ('novice_headband',  'item.novice_headband.name',  1, 'HELMET',      1),
    ('novice_wraps',     'item.novice_wraps.name',     1, 'GLOVE',       1),
    ('novice_sandals',   'item.novice_sandals.name',   1, 'BOOT',        1),
    ('novice_bracelet',  'item.novice_bracelet.name',  1, 'ACCESSORY_1', 2),
    ('novice_necklace',  'item.novice_necklace.name',  1, 'ACCESSORY_2', 2);

-- ---------------------------------------------------------------------------------------------
-- 2. Trang bị bậc "Hạ Nhẫn" — đủ 7 ô, kể cả WEAPON (bậc 2 của vũ khí).
--    Tổng bộ = +49 ATK, so với ATK gốc 30 của nhân vật mới (STR 10 × 2 + AGI 10 × 1, GDD §5) —
--    đáng để cày, chưa tới mức một bộ đồ nuốt hết mọi chỉ số gốc.
-- ---------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk) VALUES
    ('steel_katana',     'item.steel_katana.name',     1, 'WEAPON',     18),
    ('genin_vest',       'item.genin_vest.name',       1, 'ARMOR',       7),
    ('genin_headband',   'item.genin_headband.name',   1, 'HELMET',      4),
    ('genin_wraps',      'item.genin_wraps.name',      1, 'GLOVE',       4),
    ('genin_sandals',    'item.genin_sandals.name',    1, 'BOOT',        4),
    ('genin_bracelet',   'item.genin_bracelet.name',   1, 'ACCESSORY_1', 6),
    ('genin_necklace',   'item.genin_necklace.name',   1, 'ACCESSORY_2', 6);

-- ---------------------------------------------------------------------------------------------
-- 3. Vật phẩm KHÔNG mặc được (equip_slot NULL, khuôn kunai/enhance_protection_charm).
--
--    CỐ Ý KHÔNG thêm thuốc hồi máu / vật phẩm "dùng được": server CHƯA có UseItemUseCase nào —
--    thêm bình máu bây giờ là nội dung chết, người chơi bấm không có gì xảy ra. Hai dòng dưới đây
--    là nguyên liệu rơi từ quái, và chúng chạy THẬT bằng đường đã có: rơi (GrantLootUseCase) →
--    bán lấy vàng (SellItemUseCase). Đây là nguồn vàng đầu tiên của game — trước V21, người chơi
--    mới chỉ kiếm được 2 vàng/con kunai và không có cách nào mua nổi thứ gì.
-- ---------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack) VALUES
    ('iron_scrap',   'item.iron_scrap.name',   99),
    ('chakra_shard', 'item.chakra_shard.name', 99);

-- ---------------------------------------------------------------------------------------------
-- 4. Cửa hàng (shop_item, V8) — buy_price/sell_price độc lập nhau theo quyết định ở V8.
--    Trọn bộ Tập Sự = 700 vàng (gồm cả wooden_sword 100 đã có sẵn từ V8).
--    Trọn bộ Hạ Nhẫn = 10.900 vàng.
--
--    MỌI món rơi được ở §5 đều có dòng shop_item ở đây — tool load-test (SimulatedPlayer
--    §sellOneOwnedItem) dựa vào bất biến "đồ nhặt được thì bán được", giữ nguyên khi thêm món mới.
-- ---------------------------------------------------------------------------------------------
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 120, 48 FROM item_template WHERE code = 'novice_vest';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 60, 24 FROM item_template WHERE code = 'novice_headband';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 60, 24 FROM item_template WHERE code = 'novice_wraps';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 60, 24 FROM item_template WHERE code = 'novice_sandals';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 150, 60 FROM item_template WHERE code = 'novice_bracelet';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 150, 60 FROM item_template WHERE code = 'novice_necklace';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 2500, 800 FROM item_template WHERE code = 'steel_katana';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 1800, 600 FROM item_template WHERE code = 'genin_vest';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 1200, 400 FROM item_template WHERE code = 'genin_headband';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 1200, 400 FROM item_template WHERE code = 'genin_wraps';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 1200, 400 FROM item_template WHERE code = 'genin_sandals';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 1500, 500 FROM item_template WHERE code = 'genin_bracelet';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 1500, 500 FROM item_template WHERE code = 'genin_necklace';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 40, 12 FROM item_template WHERE code = 'iron_scrap';

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 400, 120 FROM item_template WHERE code = 'chakra_shard';

-- ---------------------------------------------------------------------------------------------
-- 5. Bảng rơi đồ (drop_table_entry, khuôn V4/V17) — 2 quái duy nhất đang tồn tại thật:
--    'training_dummy' và 'sample_boss' (drop_table.code khớp thẳng monsterCode, xem comment V4).
--
--    ⚠️ SỐ ENTRY PHẢI ÍT. Inventory.add() luôn APPEND, KHÔNG gộp stack (feature-inventory/domain),
--    túi chỉ có 20 ô (PostgresInventoryAdapter.DEFAULT_CAPACITY), và item rơi khi túi đầy thì MẤT
--    LUÔN (javadoc GrantLootUseCase). Mỗi entry trúng = 1 ô túi. Nên quái thường giữ ~0,85 ô/mạng
--    (kunai 50% cũ + 2 dòng mới), boss được nhiều hơn vì hiếm khi giết được.
-- ---------------------------------------------------------------------------------------------
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 30, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'training_dummy' AND it.code = 'iron_scrap';

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 5, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'training_dummy' AND it.code = 'novice_wraps';

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'sample_boss' AND it.code = 'chakra_shard';

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 8, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'sample_boss' AND it.code = 'genin_vest';

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 5, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'sample_boss' AND it.code = 'steel_katana';
