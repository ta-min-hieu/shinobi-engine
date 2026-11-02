-- V121 — Quẻ bói hằng ngày (ADR-149, lô 27). Mỗi ngày (giờ Việt Nam) một lần, đứng gần trưởng lão Chu Hoành gieo một
-- quẻ: quẻ cộng/trừ phần trăm EXP đánh quái tới hết ngày, quẻ tốt kèm chút quà. Mọi số là PLACEHOLDER (Điều 27).
CREATE TABLE fortune (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                    VARCHAR(32)  NOT NULL UNIQUE,
    name_key                VARCHAR(128) NOT NULL,
    text_key                VARCHAR(128) NOT NULL,
    weight                  INT          NOT NULL CHECK (weight > 0),
    exp_percent             INT          NOT NULL CHECK (exp_percent BETWEEN -50 AND 100),
    reward_gold             BIGINT       NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT       REFERENCES item_template (id),
    reward_quantity         INT          NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    CHECK ((reward_item_template_id IS NULL) = (reward_quantity = 0))
);

INSERT INTO fortune (code, name_key, text_key, weight, exp_percent, reward_gold, reward_item_template_id, reward_quantity)
SELECT v.code, v.name_key, v.text_key, v.weight, v.exp_percent, v.gold, it.id, v.qty
  FROM (VALUES
        (1, 'dai_cat',   'fortune.dai_cat.name',   'fortune.dai_cat.text',    8,  20,   0, 'enhance_protection_charm', 1),
        (2, 'trung_cat', 'fortune.trung_cat.name', 'fortune.trung_cat.text', 20,  10, 300, NULL,                       0),
        (3, 'tieu_cat',  'fortune.tieu_cat.name',  'fortune.tieu_cat.text',  32,   5, 100, NULL,                       0),
        (4, 'binh',      'fortune.binh.name',      'fortune.binh.text',      25,   0,   0, NULL,                       0),
        (5, 'hung',      'fortune.hung.name',      'fortune.hung.text',      15, -10,   0, NULL,                       0)
    ) AS v (ord, code, name_key, text_key, weight, exp_percent, gold, item_code, qty)
  LEFT JOIN item_template it ON it.code = v.item_code
 ORDER BY v.ord;  -- id theo đúng thứ tự trên (JOIN không giữ thứ tự VALUES)

-- Một quẻ mỗi nhân vật mỗi ngày (PRIMARY KEY chặn gieo hai lần kể cả khi hai lệnh chạy song song).
CREATE TABLE character_fortune (
    character_id BIGINT      NOT NULL REFERENCES character (id),
    day          DATE        NOT NULL,
    fortune_id   BIGINT      NOT NULL REFERENCES fortune (id),
    drawn_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, day)
);
