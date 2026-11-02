-- V124 — Quà mốc cảnh giới (ADR-153, lô 28). Đạt cấp của một mốc thì nhận quà MỘT lần (bấm nhận trong cửa sổ, túi
-- đầy thì quà vẫn chờ). Mốc = cấp mở đầu mỗi cảnh giới (ADR-136: 19/37/55/73/91) + một mốc nhập môn ở cấp 10.
-- Mọi số là PLACEHOLDER (Điều 27).
CREATE TABLE realm_milestone (
    level                   INT          PRIMARY KEY CHECK (level BETWEEN 1 AND 100),
    name_key                VARCHAR(128) NOT NULL,
    reward_gold             BIGINT       NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT       REFERENCES item_template (id),
    reward_quantity         INT          NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    CHECK ((reward_item_template_id IS NULL) = (reward_quantity = 0))
);

INSERT INTO realm_milestone (level, name_key, reward_gold, reward_item_template_id, reward_quantity)
SELECT v.level, v.name_key, v.gold, it.id, v.qty
  FROM (VALUES
        (10, 'milestone.lv10',  1000, 'minor_healing_pill',       5),
        (19, 'milestone.lv19',  3000, 'enhance_protection_charm', 1),
        (37, 'milestone.lv37',  8000, 'teleport_talisman',        3),
        (55, 'milestone.lv55', 15000, 'enhance_protection_charm', 2),
        (73, 'milestone.lv73', 30000, 'treasure_map',             3),
        (91, 'milestone.lv91', 60000, 'enhance_protection_charm', 5)
    ) AS v (level, name_key, gold, item_code, qty)
  LEFT JOIN item_template it ON it.code = v.item_code;

CREATE TABLE character_milestone_claim (
    character_id BIGINT      NOT NULL REFERENCES character (id),
    level        INT         NOT NULL REFERENCES realm_milestone (level),
    claimed_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, level)
);
