-- ADR-114 — nhiệm vụ hộ tống. Tuyến là THAM SỐ theo quest (Điều 27); nội dung (mục tiêu, thưởng) nằm trong script.
-- D4: Chu Hoành nhờ đưa xe linh thảo từ tông môn tới trạm Linh Mạch, x = 0 → 82.

INSERT INTO quest_template (code, type, script_ref, repeat_policy)
SELECT 'daily_caravan_escort', 'DAILY', '/scripts/quest/daily_caravan_escort.js', 'DAILY'
 WHERE NOT EXISTS (SELECT 1 FROM quest_template q WHERE q.code = 'daily_caravan_escort');

CREATE TABLE escort_route (
    quest_code VARCHAR(32) PRIMARY KEY REFERENCES quest_template (code),
    start_x    REAL        NOT NULL,
    end_x      REAL        NOT NULL,
    max_hp     INT         NOT NULL CHECK (max_hp > 0),
    speed_mps  REAL        NOT NULL CHECK (speed_mps > 0)
);

INSERT INTO escort_route (quest_code, start_x, end_x, max_hp, speed_mps)
VALUES ('daily_caravan_escort', 0, 82, 300, 1.5);
