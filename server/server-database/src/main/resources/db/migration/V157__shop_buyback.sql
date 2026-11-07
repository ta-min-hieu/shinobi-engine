-- ADR-202 — mua lại đồ đã bán cho cửa hàng NPC: ghi cùng transaction bán; giữ 10 món gần nhất mỗi nhân vật (cắt lúc
-- ghi); mua lại được trong 1 giờ với đúng giá đã nhận, giữ nguyên cấp cường hoá.
CREATE TABLE shop_buyback (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id      BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    item_template_id  BIGINT      NOT NULL REFERENCES item_template (id),
    quantity          INT         NOT NULL CHECK (quantity >= 1),
    enhance_level     INT         NOT NULL DEFAULT 0 CHECK (enhance_level >= 0),
    price             BIGINT      NOT NULL CHECK (price >= 0),
    sold_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_shop_buyback_character ON shop_buyback (character_id, sold_at DESC);
