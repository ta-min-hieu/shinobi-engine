-- Lô 22 phần 1 (2026-10-25): thông báo server (ADR-103), mã quà (ADR-104), báo cáo người chơi (ADR-105),
-- lịch hoạt động + điểm năng động (ADR-106).

-- ADR-103 — đúng MỘT dòng (id = 1). Không có dòng = không có thông báo.
CREATE TABLE server_notice (
    id         SMALLINT     PRIMARY KEY CHECK (id = 1),
    body       VARCHAR(300) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by BIGINT       REFERENCES character (id) ON DELETE SET NULL
);

-- ADR-104 — mã chữ hoa; uses không bao giờ vượt max_uses.
CREATE TABLE gift_code (
    code             VARCHAR(24) PRIMARY KEY CHECK (code ~ '^[A-Z0-9_-]{4,24}$'),
    gold             BIGINT      NOT NULL DEFAULT 0 CHECK (gold >= 0),
    item_template_id BIGINT      REFERENCES item_template (id),
    quantity         INT         NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    max_uses         INT         NOT NULL CHECK (max_uses > 0),
    uses             INT         NOT NULL DEFAULT 0 CHECK (uses >= 0 AND uses <= max_uses),
    expires_at       TIMESTAMPTZ NOT NULL,
    created_by       BIGINT      REFERENCES character (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CHECK (gold > 0 OR (item_template_id IS NOT NULL AND quantity > 0))
);

-- Khoá chính chặn một nhân vật đổi một mã hai lần. mailed = thư quà đã nằm trong hộp.
CREATE TABLE gift_code_redemption (
    code         VARCHAR(24) NOT NULL REFERENCES gift_code (code) ON DELETE CASCADE,
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    redeemed_at  TIMESTAMPTZ NOT NULL,
    mailed       BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (code, character_id)
);

-- ADR-105 — chat_excerpt trích lúc báo cáo (JSON mảng các tin gần nhất), không phụ thuộc audit_log còn hay đã dọn.
CREATE TABLE player_report (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id  BIGINT       NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    target_id    BIGINT       NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    reason       VARCHAR(8)   NOT NULL CHECK (reason IN ('CHAT', 'CHEAT', 'SCAM', 'OTHER')),
    note         VARCHAR(200) NOT NULL DEFAULT '',
    chat_excerpt TEXT         NOT NULL DEFAULT '[]',
    created_at   TIMESTAMPTZ  NOT NULL,
    handled_at   TIMESTAMPTZ,
    handled_by   BIGINT       REFERENCES character (id) ON DELETE SET NULL,
    CHECK (reporter_id <> target_id)
);

CREATE INDEX player_report_reporter_idx ON player_report (reporter_id, created_at);
CREATE INDEX player_report_open_idx ON player_report (created_at) WHERE handled_at IS NULL;

-- ADR-106 — THAM SỐ (Điều 27).
CREATE TABLE activity_definition (
    code        VARCHAR(32) PRIMARY KEY,
    name_key    VARCHAR(64) NOT NULL,
    points_each INT         NOT NULL CHECK (points_each > 0),
    max_count   INT         NOT NULL CHECK (max_count > 0),
    sort_order  INT         NOT NULL
);

INSERT INTO activity_definition (code, name_key, points_each, max_count, sort_order) VALUES
    ('check_in',      'activity.check_in',      10, 1, 1),
    ('online_reward', 'activity.online_reward', 10, 3, 2),
    ('bounty',        'activity.bounty',        10, 5, 3),
    ('dungeon_run',   'activity.dungeon_run',   10, 3, 4),
    ('daily_quest',   'activity.daily_quest',   10, 3, 5),
    ('herb_gather',   'activity.herb_gather',    5, 4, 6);

CREATE TABLE activity_chest (
    milestone        INT    PRIMARY KEY CHECK (milestone > 0),
    gold             BIGINT NOT NULL DEFAULT 0 CHECK (gold >= 0),
    item_template_id BIGINT REFERENCES item_template (id),
    quantity         INT    NOT NULL DEFAULT 0 CHECK (quantity >= 0)
);

INSERT INTO activity_chest (milestone, gold, item_template_id, quantity)
SELECT m.milestone, m.gold, it.id, m.quantity
  FROM (VALUES (20, 500, 'minor_healing_pill', 2),
               (40, 1000, 'spirit_herb', 3),
               (60, 1500, 'teleport_talisman', 1),
               (80, 2000, 'spirit_beast_pill', 1),
               (100, 3000, 'enhance_protection_charm', 1)) AS m (milestone, gold, code, quantity)
  LEFT JOIN item_template it ON it.code = m.code;

-- Số lần mỗi việc theo NGÀY giờ Việt Nam. count có thể vượt max_count — điểm mới là thứ bị kẹp.
CREATE TABLE activity_progress (
    character_id  BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    day           DATE        NOT NULL,
    activity_code VARCHAR(32) NOT NULL REFERENCES activity_definition (code),
    count         INT         NOT NULL CHECK (count >= 0),
    PRIMARY KEY (character_id, day, activity_code)
);

CREATE TABLE activity_chest_claim (
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    day          DATE        NOT NULL,
    milestone    INT         NOT NULL REFERENCES activity_chest (milestone),
    claimed_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, day, milestone)
);
