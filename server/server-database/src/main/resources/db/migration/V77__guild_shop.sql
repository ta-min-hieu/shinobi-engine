-- ADR-080 — cửa hàng bang. Cống hiến tích luỹ (contribution) GIỮ NGUYÊN làm thước đo công lao; điểm tiêu được =
-- contribution − contribution_spent. Hàng + giá + cấp bang yêu cầu là THAM SỐ (Điều 27).
ALTER TABLE guild_member ADD COLUMN contribution_spent BIGINT NOT NULL DEFAULT 0 CHECK (contribution_spent >= 0);

CREATE TABLE guild_shop_item (
    item_template_id     BIGINT PRIMARY KEY REFERENCES item_template (id),
    contribution_cost    BIGINT NOT NULL CHECK (contribution_cost >= 1),
    required_guild_level INT    NOT NULL CHECK (required_guild_level BETWEEN 1 AND 5),
    sort_order           INT    NOT NULL DEFAULT 0
);

INSERT INTO guild_shop_item (item_template_id, contribution_cost, required_guild_level, sort_order)
SELECT it.id, v.cost, v.lvl, v.sort
  FROM (VALUES ('minor_healing_pill',       200,   1, 1),
               ('enhance_protection_charm', 3000,  2, 2),
               ('spirit_jade_fire',         2000,  3, 3),
               ('spirit_jade_earth',        2000,  3, 4),
               ('spirit_jade_water',        2000,  3, 5),
               ('spirit_jade_wood',         2000,  3, 6),
               ('spirit_beast_pill',        15000, 4, 7),
               ('faction_change_pill',      20000, 5, 8)) AS v (code, cost, lvl, sort)
  JOIN item_template it ON it.code = v.code
ON CONFLICT (item_template_id) DO NOTHING;
