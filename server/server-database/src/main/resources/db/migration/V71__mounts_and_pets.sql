-- ADR-070 — phi kiếm (ô MOUNT, chỉ tốc độ); ADR-072 — linh thú (ô PET, cấp riêng). Mọi con số là THAM SỐ
-- (Điều 27): giá, cấp yêu cầu, tốc độ, chỉ số linh thú chỉnh bằng SQL.

ALTER TABLE item_template ADD COLUMN move_speed_percent INT NOT NULL DEFAULT 0
    CHECK (move_speed_percent BETWEEN 0 AND 100);

-- Phi kiếm: KHÔNG cộng chỉ số nào (GDD §5 — pay-for-cosmetic sạch), chỉ tốc độ.
INSERT INTO item_template (code, name_key, max_stack, equip_slot, required_level, move_speed_percent)
SELECT v.code, v.name_key, 1, 'MOUNT', v.lvl, v.speed
  FROM (VALUES ('wooden_flying_sword', 'item.wooden_flying_sword.name', 15, 20),
               ('jade_flying_sword',   'item.jade_flying_sword.name',   25, 35)) AS v (code, name_key, lvl, speed)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = v.code);

-- Linh thú: chỉ số GỐC ở cấp 1; mỗi cấp linh thú +10% (cùng công thức cường hoá — ADR-072 D2).
INSERT INTO item_template (code, name_key, max_stack, equip_slot, required_level,
                           bonus_atk, bonus_def, bonus_res, bonus_hp)
SELECT v.code, v.name_key, 1, 'PET', 20, v.atk, v.def, v.res, v.hp
  FROM (VALUES ('spirit_fox',    'item.spirit_fox.name',    3, 0, 0, 10),
               ('spirit_turtle', 'item.spirit_turtle.name', 0, 3, 3, 15)) AS v (code, name_key, atk, def, res, hp)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = v.code);

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id,
       CASE code WHEN 'wooden_flying_sword' THEN 8000
                 WHEN 'jade_flying_sword'   THEN 30000
                 ELSE 12000 END,
       CASE code WHEN 'wooden_flying_sword' THEN 800
                 WHEN 'jade_flying_sword'   THEN 3000
                 ELSE 1200 END
  FROM item_template
 WHERE code IN ('wooden_flying_sword', 'jade_flying_sword', 'spirit_fox', 'spirit_turtle');

-- Cấp linh thú theo dòng túi đồ. Chưa có dòng = cấp 1 (tạo ở lần nhận EXP đầu). Dòng túi bị xoá ⇒ dòng này đi theo.
CREATE TABLE pet_state (
    inventory_item_id  BIGINT PRIMARY KEY REFERENCES inventory_item (id) ON DELETE CASCADE,
    level              INT    NOT NULL CHECK (level BETWEEN 1 AND 30),
    exp                BIGINT NOT NULL CHECK (exp >= 0)
);
