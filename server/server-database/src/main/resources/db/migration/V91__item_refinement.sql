-- ADR-111 — tẩy luyện pháp khí. Mỗi món tối đa 3 dòng chỉ số phụ do server gieo; dòng đi theo DÒNG TÚI (xoá món —
-- bán, phân giải — thì dòng mất theo). Giá đá (1 / khoá dòng 3) và biên độ dòng là THAM SỐ ở RefinementPolicy.

INSERT INTO item_template (code, name_key, max_stack)
SELECT 'refine_stone', 'item.refine_stone.name', 99
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'refine_stone');

CREATE TABLE item_refinement (
    inventory_item_id BIGINT      NOT NULL REFERENCES inventory_item (id) ON DELETE CASCADE,
    line_index        INT         NOT NULL CHECK (line_index BETWEEN 0 AND 2),
    stat              VARCHAR(8)  NOT NULL CHECK (stat IN ('ATK', 'DEF', 'RES', 'HP')),
    value             INT         NOT NULL CHECK (value > 0),
    PRIMARY KEY (inventory_item_id, line_index)
);

-- Nguồn bản đầu (D1): cửa hàng tông môn 200, bán lại 20; cửa hàng danh vọng bậc 1, 40 điểm.
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 200, 20 FROM item_template WHERE code = 'refine_stone'
ON CONFLICT DO NOTHING;

INSERT INTO reputation_shop_item (item_template_id, price, required_tier, sort_order)
SELECT id, 40, 1, 6 FROM item_template WHERE code = 'refine_stone'
ON CONFLICT DO NOTHING;
