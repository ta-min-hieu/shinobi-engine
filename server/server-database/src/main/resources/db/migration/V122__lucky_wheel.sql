-- V122 — Vòng quay may mắn (ADR-151, lô 27). Mỗi ngày (giờ Việt Nam) một lượt miễn phí, rồi tối đa 10 lượt mua bằng
-- linh thạch (LuckyWheelRules); server quay theo trọng số, thưởng + trừ tiền + audit trong MỘT giao dịch.
-- Mọi số là PLACEHOLDER (Điều 27) — vòng quay cố ý là chỗ HÚT linh thạch (giá trị kỳ vọng < giá một lượt).
CREATE TABLE wheel_prize (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                    VARCHAR(32)  NOT NULL UNIQUE,
    label_key               VARCHAR(128) NOT NULL,
    weight                  INT          NOT NULL CHECK (weight > 0),
    reward_gold             BIGINT       NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT       REFERENCES item_template (id),
    reward_quantity         INT          NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    CHECK ((reward_item_template_id IS NULL) = (reward_quantity = 0)),
    CHECK (reward_gold > 0 OR reward_item_template_id IS NOT NULL)
);

INSERT INTO wheel_prize (code, label_key, weight, reward_gold, reward_item_template_id, reward_quantity)
SELECT v.code, v.label_key, v.weight, v.gold, it.id, v.qty
  FROM (VALUES
        (1, 'small_gold',   'wheel.prize.small_gold',   30,   200, NULL,                       0),
        (2, 'pills',        'wheel.prize.pills',        20,     0, 'minor_healing_pill',       2),
        (3, 'herbs',        'wheel.prize.herbs',        15,     0, 'spirit_herb',              5),
        (4, 'medium_gold',  'wheel.prize.medium_gold',  12,   800, NULL,                       0),
        (5, 'teleport',     'wheel.prize.teleport',     10,     0, 'teleport_talisman',        1),
        (6, 'charm',        'wheel.prize.charm',         7,     0, 'enhance_protection_charm', 1),
        (7, 'treasure_map', 'wheel.prize.treasure_map',  5,     0, 'treasure_map',             1),
        (8, 'jackpot',      'wheel.prize.jackpot',       1, 10000, NULL,                       0)
    ) AS v (ord, code, label_key, weight, gold, item_code, qty)
  LEFT JOIN item_template it ON it.code = v.item_code
 ORDER BY v.ord;  -- id theo đúng thứ tự trên (JOIN không giữ thứ tự VALUES) — client hiển thị theo thứ tự này

-- Mỗi lượt quay một dòng: đếm lượt miễn phí/mua trong ngày, và là sổ đối chiếu cho GM.
CREATE TABLE character_wheel_spin (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT      NOT NULL REFERENCES character (id),
    day          DATE        NOT NULL,
    paid         BOOLEAN     NOT NULL,
    prize_id     BIGINT      NOT NULL REFERENCES wheel_prize (id),
    spun_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX character_wheel_spin_by_day ON character_wheel_spin (character_id, day);
