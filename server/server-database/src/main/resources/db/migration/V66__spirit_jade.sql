-- ADR-064 — Linh Ngọc khảm nạm. Bốn loại ngọc (xếp chồng, không mặc được, không "dùng" được), mỗi trang bị
-- một ô khảm, bảo hiểm theo nhân vật. Mọi con số là THAM SỐ (Điều 27): giá, chỉ số ngọc chỉnh bằng SQL.

INSERT INTO item_template (code, name_key, max_stack)
SELECT v.code, v.name_key, 99
  FROM (VALUES ('spirit_jade_fire',  'item.spirit_jade_fire.name'),
               ('spirit_jade_earth', 'item.spirit_jade_earth.name'),
               ('spirit_jade_water', 'item.spirit_jade_water.name'),
               ('spirit_jade_wood',  'item.spirit_jade_wood.name')) AS v (code, name_key)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = v.code);

-- Chỉ số mỗi viên cộng PHẲNG vào món đang khảm (không nhân theo cường hoá — D4).
CREATE TABLE gem_template (
    item_template_id  BIGINT PRIMARY KEY REFERENCES item_template (id),
    bonus_atk         INT NOT NULL DEFAULT 0 CHECK (bonus_atk >= 0),
    bonus_def         INT NOT NULL DEFAULT 0 CHECK (bonus_def >= 0),
    bonus_res         INT NOT NULL DEFAULT 0 CHECK (bonus_res >= 0),
    bonus_hp          INT NOT NULL DEFAULT 0 CHECK (bonus_hp >= 0)
);

INSERT INTO gem_template (item_template_id, bonus_atk, bonus_def, bonus_res, bonus_hp)
SELECT id,
       CASE code WHEN 'spirit_jade_fire'  THEN 3 ELSE 0 END,
       CASE code WHEN 'spirit_jade_earth' THEN 3 ELSE 0 END,
       CASE code WHEN 'spirit_jade_water' THEN 3 ELSE 0 END,
       CASE code WHEN 'spirit_jade_wood'  THEN 15 ELSE 0 END
  FROM item_template
 WHERE code IN ('spirit_jade_fire', 'spirit_jade_earth', 'spirit_jade_water', 'spirit_jade_wood');

-- Nguồn đầu tiên: cửa hàng tông môn (D1). Bán lại 100 — mua 1.500, khảm thêm 200: sink.
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 1500, 100 FROM item_template
 WHERE code IN ('spirit_jade_fire', 'spirit_jade_earth', 'spirit_jade_water', 'spirit_jade_wood');

-- Một ô mỗi trang bị (D2). Dòng túi bị xoá ⇒ ô đi theo (bán cho cửa hàng: ngọc mất cùng món — D5).
CREATE TABLE item_socket (
    inventory_item_id     BIGINT PRIMARY KEY REFERENCES inventory_item (id) ON DELETE CASCADE,
    gem_item_template_id  BIGINT NOT NULL REFERENCES gem_template (item_template_id),
    socketed_at           TIMESTAMPTZ NOT NULL
);

-- Bảo hiểm: số lần hỏng LIÊN TIẾP của nhân vật (D3).
CREATE TABLE gem_pity (
    character_id  BIGINT PRIMARY KEY REFERENCES character (id),
    failures      INT    NOT NULL CHECK (failures >= 0)
);
