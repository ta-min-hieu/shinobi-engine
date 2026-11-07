-- ADR-196 — theo dõi phường thị: báo người theo dõi khi có ai bày bán loại vật phẩm đó với giá mỗi cái không quá
-- mức họ đặt. Tối đa 10 dòng mỗi nhân vật (kiểm ở adapter, trong transaction có khoá dòng nhân vật).
CREATE TABLE market_watch (
    character_id      BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    item_template_id  BIGINT      NOT NULL REFERENCES item_template (id),
    max_unit_price    BIGINT      NOT NULL CHECK (max_unit_price > 0),
    created_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, item_template_id)
);

CREATE INDEX idx_market_watch_item ON market_watch (item_template_id, max_unit_price);
