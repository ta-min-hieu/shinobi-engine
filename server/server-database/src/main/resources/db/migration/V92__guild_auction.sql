-- ADR-112 — đấu giá bang. Lô sinh khi boss Bí Cảnh chết mà người ra đòn cuối có bang; vàng người trả bị giữ ngay,
-- hoàn qua thư khi bị trả vượt; chốt một lần khi hết giờ. Mọi con số (giá, thời gian) ở GuildAuctionRules.

-- Bảng rơi của lô (tham số): một dòng được gieo theo trọng số.
CREATE TABLE guild_auction_drop (
    item_template_id BIGINT PRIMARY KEY REFERENCES item_template (id),
    quantity         INT    NOT NULL CHECK (quantity > 0),
    weight           INT    NOT NULL CHECK (weight > 0)
);

INSERT INTO guild_auction_drop (item_template_id, quantity, weight)
SELECT it.id, v.quantity, v.weight
  FROM (VALUES ('spirit_jade_fire',         1, 20),
               ('spirit_jade_earth',        1, 20),
               ('spirit_jade_water',        1, 20),
               ('spirit_jade_wood',         1, 20),
               ('refine_stone',             5, 30),
               ('enhance_protection_charm', 1, 10)) AS v (code, quantity, weight)
  JOIN item_template it ON it.code = v.code
ON CONFLICT DO NOTHING;

-- guild_id SET NULL (không CASCADE): bang giải tán giữa chừng thì vàng đang giữ chỗ vẫn phải về đúng người khi chốt.
CREATE TABLE guild_auction_lot (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id          BIGINT      REFERENCES guild (id) ON DELETE SET NULL,
    item_template_id  BIGINT      NOT NULL REFERENCES item_template (id),
    quantity          INT         NOT NULL CHECK (quantity > 0),
    killer_id         BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    current_bid       BIGINT      NOT NULL DEFAULT 0 CHECK (current_bid >= 0),
    leader_id         BIGINT      REFERENCES character (id) ON DELETE SET NULL,
    opened_at         TIMESTAMPTZ NOT NULL,
    ends_at           TIMESTAMPTZ NOT NULL,
    settled_at        TIMESTAMPTZ
);

CREATE INDEX guild_auction_lot_open ON guild_auction_lot (guild_id, ends_at) WHERE settled_at IS NULL;

CREATE TABLE guild_auction_participant (
    lot_id       BIGINT NOT NULL REFERENCES guild_auction_lot (id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    PRIMARY KEY (lot_id, character_id)
);

-- Hàng đợi thư bền: ghi trong giao dịch trả giá/chốt, xoá khi thư đã nằm trong hộp. Khoá chống trùng của thư suy từ id.
-- lot_id không FK: thư hoàn tiền phải sống sót cả khi dòng lô bị dọn.
CREATE TABLE guild_auction_payout (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_id           BIGINT      NOT NULL,
    character_id     BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    kind             VARCHAR(8)  NOT NULL CHECK (kind IN ('REFUND', 'ITEM', 'SHARE')),
    gold             BIGINT      NOT NULL DEFAULT 0 CHECK (gold >= 0),
    item_template_id BIGINT      REFERENCES item_template (id),
    quantity         INT         NOT NULL DEFAULT 0 CHECK (quantity >= 0)
);
