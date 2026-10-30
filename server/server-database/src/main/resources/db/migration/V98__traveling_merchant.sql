-- V98 — thương nhân du hành (ADR-118, lô 23). Server tự lên lịch 2–3 lần ghé mỗi ngày (giờ VN) ở các điểm truyền
-- tống, mỗi lần 30 phút, mang 3 món rút theo trọng số từ traveling_merchant_offer; số lượng mỗi món là CHUNG cả server.
-- Giá/số lượng/trọng số bên dưới là ĐỀ XUẤT CÂN BẰNG ban đầu, chưa playtest.
CREATE TABLE traveling_merchant_offer (
    item_template_id   BIGINT PRIMARY KEY REFERENCES item_template (id),
    price              BIGINT NOT NULL CHECK (price > 0),
    quantity_per_visit INT    NOT NULL CHECK (quantity_per_visit >= 1),
    weight             INT    NOT NULL CHECK (weight >= 1)
);

CREATE TABLE traveling_merchant_visit (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    waypoint_code VARCHAR(32) NOT NULL REFERENCES waypoint (code),
    starts_at     TIMESTAMPTZ NOT NULL,
    ends_at       TIMESTAMPTZ NOT NULL,
    announced     BOOLEAN     NOT NULL DEFAULT FALSE,
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_merchant_visit_starts ON traveling_merchant_visit (starts_at);

CREATE TABLE traveling_merchant_stock (
    visit_id         BIGINT NOT NULL REFERENCES traveling_merchant_visit (id) ON DELETE CASCADE,
    item_template_id BIGINT NOT NULL REFERENCES item_template (id),
    price            BIGINT NOT NULL CHECK (price > 0),
    remaining        INT    NOT NULL CHECK (remaining >= 0),
    PRIMARY KEY (visit_id, item_template_id)
);

INSERT INTO traveling_merchant_offer (item_template_id, price, quantity_per_visit, weight)
SELECT t.id, o.price, o.qty, o.weight
  FROM (VALUES ('refine_stone', 150, 10, 10),
               ('spirit_jade_fire', 400, 3, 4),
               ('spirit_jade_earth', 400, 3, 4),
               ('spirit_jade_water', 400, 3, 4),
               ('spirit_jade_wood', 400, 3, 4),
               ('spirit_beast_pill', 250, 5, 6),
               ('revive_pill', 220, 5, 8),
               ('teleport_talisman', 60, 10, 8),
               ('bag_expansion_pouch', 1500, 1, 2),
               ('faction_change_pill', 2000, 1, 1)) AS o (code, price, qty, weight)
  JOIN item_template t ON t.code = o.code
ON CONFLICT DO NOTHING;
