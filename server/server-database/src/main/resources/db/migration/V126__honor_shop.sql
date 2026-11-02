-- V126 — Cửa hàng điểm vinh dự đấu trường (ADR-155, lô 28). Mỗi trận đấu trường chốt xong: thắng +10, thua +3, tối đa
-- 100 điểm mỗi ngày giờ Việt Nam (chống hai người quen đánh nhau cày điểm). Đổi vật phẩm hiếm, mỗi món có trần số lần
-- đổi mỗi tuần. Mọi số là PLACEHOLDER (Điều 27).
CREATE TABLE character_honor (
    character_id BIGINT PRIMARY KEY REFERENCES character (id),
    earned       BIGINT NOT NULL DEFAULT 0 CHECK (earned >= 0),
    spent        BIGINT NOT NULL DEFAULT 0 CHECK (spent >= 0 AND spent <= earned)
);

CREATE TABLE character_honor_day (
    character_id BIGINT NOT NULL REFERENCES character (id),
    day          DATE   NOT NULL,
    gained       INT    NOT NULL DEFAULT 0 CHECK (gained >= 0),
    PRIMARY KEY (character_id, day)
);

CREATE TABLE honor_shop_item (
    item_template_id BIGINT PRIMARY KEY REFERENCES item_template (id),
    price            INT    NOT NULL CHECK (price > 0),
    quantity         INT    NOT NULL CHECK (quantity > 0),
    weekly_limit     INT    NOT NULL CHECK (weekly_limit > 0),
    sort_order       INT    NOT NULL
);

INSERT INTO honor_shop_item (item_template_id, price, quantity, weekly_limit, sort_order)
SELECT it.id, v.price, v.qty, v.weekly, v.ord
  FROM (VALUES ('teleport_talisman',        20, 2, 5, 1),
               ('broadcast_talisman',       30, 1, 2, 2),
               ('treasure_map',             40, 1, 5, 3),
               ('revive_pill',              50, 1, 3, 4),
               ('enhance_protection_charm', 60, 1, 3, 5)) AS v (code, price, qty, weekly, ord)
  JOIN item_template it ON it.code = v.code;

CREATE TABLE character_honor_purchase (
    character_id     BIGINT NOT NULL REFERENCES character (id),
    week_start       DATE   NOT NULL,
    item_template_id BIGINT NOT NULL REFERENCES honor_shop_item (item_template_id),
    bought           INT    NOT NULL DEFAULT 0 CHECK (bought >= 0),
    PRIMARY KEY (character_id, week_start, item_template_id)
);
