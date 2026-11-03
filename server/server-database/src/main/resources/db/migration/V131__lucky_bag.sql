-- ADR-163 (2026-11-03) — Túi phúc. Một vật phẩm mở ra MỘT phần quà rút theo trọng số (server rút). Bảng quà công khai
-- (client xem trước % trước khi mở). Mọi con số là THAM SỐ (Điều 27) — PLACEHOLDER cân bằng.

INSERT INTO item_template (code, name_key, max_stack)
SELECT 'lucky_bag', 'item.lucky_bag.name', 99
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'lucky_bag');

CREATE TABLE lucky_bag_reward (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bag_item_template_id    BIGINT NOT NULL REFERENCES item_template (id),
    reward_gold             BIGINT NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT REFERENCES item_template (id),
    reward_quantity         INT    NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    weight                  INT    NOT NULL CHECK (weight > 0),
    sort_order              INT    NOT NULL,
    -- Mỗi phần quà là vàng HOẶC một chồng đồ có số lượng.
    CHECK ((reward_item_template_id IS NULL AND reward_gold > 0 AND reward_quantity = 0)
        OR (reward_item_template_id IS NOT NULL AND reward_gold = 0 AND reward_quantity > 0))
);

CREATE INDEX idx_lucky_bag_reward_bag ON lucky_bag_reward (bag_item_template_id);

-- Tổng trọng số 100 ⇒ trọng số chính là %.
INSERT INTO lucky_bag_reward
       (bag_item_template_id, reward_gold, reward_item_template_id, reward_quantity, weight, sort_order)
SELECT bag.id, v.gold, item.id, v.quantity, v.weight, v.sort_order
  FROM (VALUES (0,    'minor_healing_pill',       3, 25, 1),
               (800,  NULL,                       0, 30, 2),
               (0,    'enhance_protection_charm', 1, 15, 3),
               (0,    'teleport_talisman',        2, 10, 4),
               (0,    'spirit_jade_fire',         1,  3, 5),
               (0,    'spirit_jade_earth',        1,  3, 6),
               (0,    'spirit_jade_water',        1,  2, 7),
               (0,    'spirit_jade_wood',         1,  2, 8),
               (0,    'spirit_jade_wood_2',       1,  5, 9),
               (5000, NULL,                       0,  5, 10)) AS v (gold, item_code, quantity, weight, sort_order)
  JOIN item_template bag ON bag.code = 'lucky_bag'
  LEFT JOIN item_template item ON item.code = v.item_code;

-- Nguồn: boss Bí Cảnh 25% một túi, boss thế giới chắc chắn 1–2 túi.
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT d.id, it.id, v.chance, v.min_q, v.max_q
  FROM (VALUES ('sample_boss', 25, 1, 1), ('demon_king', 100, 1, 2)) AS v (table_code, chance, min_q, max_q)
  JOIN drop_table d ON d.code = v.table_code
  JOIN item_template it ON it.code = 'lucky_bag';
