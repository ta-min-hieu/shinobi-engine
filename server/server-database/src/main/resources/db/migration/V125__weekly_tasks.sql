-- V125 — Nhiệm vụ tuần cá nhân (ADR-154, lô 28). Năm việc cố định; tiến độ đếm theo tuần (thứ Hai 0h giờ Việt Nam), đủ
-- chỉ tiêu thì bấm nhận thưởng MỘT lần mỗi tuần. Đếm qua cùng đường với lịch hoạt động (ADR-106) + "hạ quái".
-- Mọi số là PLACEHOLDER (Điều 27).
CREATE TABLE weekly_task (
    code                    VARCHAR(32)  PRIMARY KEY,
    name_key                VARCHAR(128) NOT NULL,
    activity_code           VARCHAR(32)  NOT NULL,
    target                  INT          NOT NULL CHECK (target > 0),
    reward_gold             BIGINT       NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT       REFERENCES item_template (id),
    reward_quantity         INT          NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    sort_order              INT          NOT NULL,
    CHECK ((reward_item_template_id IS NULL) = (reward_quantity = 0))
);

INSERT INTO weekly_task (code, name_key, activity_code, target, reward_gold, reward_item_template_id, reward_quantity,
                         sort_order)
SELECT v.code, v.name_key, v.activity, v.target, v.gold, it.id, v.qty, v.ord
  FROM (VALUES
        (1, 'monster_hunter', 'weekly.monster_hunter', 'monster_kill', 300, 3000, 'enhance_protection_charm', 1),
        (2, 'herbalist',      'weekly.herbalist',      'herb_gather',   20, 1500, 'treasure_map',             1),
        (3, 'delver',         'weekly.delver',         'dungeon_run',    5, 4000, 'enhance_protection_charm', 2),
        (4, 'bounty_hunter',  'weekly.bounty_hunter',  'bounty',         5, 2500, 'teleport_talisman',        2),
        (5, 'diligent',       'weekly.diligent',       'daily_quest',   10, 3000, 'minor_healing_pill',       5)
    ) AS v (ord, code, name_key, activity, target, gold, item_code, qty)
  LEFT JOIN item_template it ON it.code = v.item_code;

CREATE TABLE character_weekly_task (
    character_id BIGINT      NOT NULL REFERENCES character (id),
    week_start   DATE        NOT NULL,
    task_code    VARCHAR(32) NOT NULL REFERENCES weekly_task (code),
    progress     INT         NOT NULL DEFAULT 0 CHECK (progress >= 0),
    claimed_at   TIMESTAMPTZ,
    PRIMARY KEY (character_id, week_start, task_code)
);
