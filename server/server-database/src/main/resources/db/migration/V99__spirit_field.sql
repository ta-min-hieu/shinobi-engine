-- V99 — linh điền (ADR-119, lô 23). Mỗi nhân vật 4 ô (mở thêm tới 8 bằng linh thạch); gieo hạt giống, chín sau thời gian
-- THẬT, thu hoạch ra linh thảo; bạn bè tưới một lần mỗi vụ ⇒ sản lượng kỳ vọng +10%. Số phút/sản lượng/giá hạt là ĐỀ
-- XUẤT CÂN BẰNG ban đầu.
INSERT INTO item_template (code, name_key, max_stack)
SELECT s.code, s.name_key, 99
  FROM (VALUES ('spirit_herb_seed', 'item.spirit_herb_seed.name'),
               ('fine_spirit_herb_seed', 'item.fine_spirit_herb_seed.name')) AS s (code, name_key)
 WHERE NOT EXISTS (SELECT 1 FROM item_template t WHERE t.code = s.code);

CREATE TABLE field_seed (
    item_template_id       BIGINT PRIMARY KEY REFERENCES item_template (id),
    grow_minutes           INT    NOT NULL CHECK (grow_minutes >= 1),
    yield_item_template_id BIGINT NOT NULL REFERENCES item_template (id),
    yield_quantity         INT    NOT NULL CHECK (yield_quantity >= 1)
);

INSERT INTO field_seed (item_template_id, grow_minutes, yield_item_template_id, yield_quantity)
SELECT seed.id, v.minutes, herb.id, v.qty
  FROM (VALUES ('spirit_herb_seed', 60, 3), ('fine_spirit_herb_seed', 240, 14)) AS v (code, minutes, qty)
  JOIN item_template seed ON seed.code = v.code
  JOIN item_template herb ON herb.code = 'spirit_herb'
ON CONFLICT DO NOTHING;

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT t.id, v.buy, v.sell
  FROM (VALUES ('spirit_herb_seed', 120, 10), ('fine_spirit_herb_seed', 400, 40)) AS v (code, buy, sell)
  JOIN item_template t ON t.code = v.code
 WHERE NOT EXISTS (SELECT 1 FROM shop_item s WHERE s.item_template_id = t.id);

CREATE TABLE spirit_field (
    character_id BIGINT PRIMARY KEY REFERENCES character (id),
    plots        INT    NOT NULL DEFAULT 4 CHECK (plots BETWEEN 4 AND 8)
);

CREATE TABLE spirit_field_plot (
    character_id          BIGINT      NOT NULL REFERENCES character (id),
    plot_index            INT         NOT NULL CHECK (plot_index BETWEEN 0 AND 7),
    seed_item_template_id BIGINT      NOT NULL REFERENCES field_seed (item_template_id),
    planted_at            TIMESTAMPTZ NOT NULL,
    ripe_at               TIMESTAMPTZ NOT NULL,
    watered_by            BIGINT      REFERENCES character (id),
    PRIMARY KEY (character_id, plot_index)
);
