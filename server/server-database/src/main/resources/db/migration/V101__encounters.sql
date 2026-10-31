-- V101 — kỳ ngộ (ADR-121, lô 23). Hạ quái ở world chính có 2% gặp một kỳ ngộ (tối đa 3/ngày giờ VN, một kỳ ngộ chờ
-- mỗi lúc, 10 phút không chọn thì tan): một câu hỏi + vài lựa chọn, mỗi lựa chọn thưởng linh thạch/vật phẩm và/hoặc gọi
-- ra một con quái cạnh người chơi. Nội dung là DỮ LIỆU (chữ ở bundle i18n), không phải script — xem ADR-121 D3.
CREATE TABLE encounter (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(64)  NOT NULL UNIQUE,
    prompt_key VARCHAR(128) NOT NULL,
    weight     INT          NOT NULL CHECK (weight > 0)
);

CREATE TABLE encounter_option (
    encounter_id            BIGINT       NOT NULL REFERENCES encounter (id) ON DELETE CASCADE,
    option_index            INT          NOT NULL CHECK (option_index BETWEEN 0 AND 3),
    label_key               VARCHAR(128) NOT NULL,
    result_key              VARCHAR(128) NOT NULL,
    reward_gold             BIGINT       NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT       REFERENCES item_template (id),
    reward_quantity         INT          NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    spawn_monster_code      VARCHAR(32),
    PRIMARY KEY (encounter_id, option_index),
    CHECK ((reward_item_template_id IS NULL) = (reward_quantity = 0))
);

CREATE TABLE character_encounter (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id  BIGINT      NOT NULL REFERENCES character (id),
    encounter_id  BIGINT      NOT NULL REFERENCES encounter (id),
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    resolved_at   TIMESTAMPTZ,
    chosen_option INT
);

CREATE INDEX character_encounter_by_day ON character_encounter (character_id, created_at);

INSERT INTO encounter (code, prompt_key, weight) VALUES
    ('wandering_elder', 'encounter.wandering_elder.prompt', 3),
    ('spirit_spring',   'encounter.spirit_spring.prompt',   2),
    ('sealed_stele',    'encounter.sealed_stele.prompt',    1);

INSERT INTO encounter_option
    (encounter_id, option_index, label_key, result_key, reward_gold, reward_item_template_id, reward_quantity,
     spawn_monster_code)
SELECT e.id, v.idx, v.label, v.result, v.gold, it.id, v.qty, v.monster
  FROM (VALUES
        ('wandering_elder', 0, 'encounter.wandering_elder.opt0', 'encounter.wandering_elder.res0', 150, NULL, 0, NULL),
        ('wandering_elder', 1, 'encounter.wandering_elder.opt1', 'encounter.wandering_elder.res1', 0,
            'minor_healing_pill', 2, NULL),
        ('wandering_elder', 2, 'encounter.wandering_elder.opt2', 'encounter.wandering_elder.res2', 0, NULL, 0,
            'wandering_bandit'),
        ('spirit_spring',   0, 'encounter.spirit_spring.opt0',   'encounter.spirit_spring.res0',   0,
            'spirit_herb', 3, NULL),
        ('spirit_spring',   1, 'encounter.spirit_spring.opt1',   'encounter.spirit_spring.res1',   80, NULL, 0, NULL),
        ('sealed_stele',    0, 'encounter.sealed_stele.opt0',    'encounter.sealed_stele.res0',    100, NULL, 0, NULL),
        ('sealed_stele',    1, 'encounter.sealed_stele.opt1',    'encounter.sealed_stele.res1',    300, NULL, 0,
            'stone_ogre')
    ) AS v (code, idx, label, result, gold, item_code, qty, monster)
  JOIN encounter e ON e.code = v.code
  LEFT JOIN item_template it ON it.code = v.item_code;
