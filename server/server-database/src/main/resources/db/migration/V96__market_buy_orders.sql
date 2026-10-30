-- V96 — cầu mua ở phường thị (ADR-116, lô 23). Người đặt ký quỹ quantity × unit_price linh thạch lúc đặt; người bán
-- bán thẳng vào lệnh (nhận tiền từ ký quỹ, trừ phí 5% như treo bán). Vòng đời: ACTIVE → FILLED | CANCELLED | EXPIRED.
-- EXPIRED rồi mới gửi thư hoàn phần ký quỹ còn lại, đánh dấu refunded. Mọi chuyển trạng thái compare-and-set trên status.
CREATE TABLE market_buy_order (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    buyer_id         BIGINT      NOT NULL REFERENCES character (id),
    item_template_id BIGINT      NOT NULL REFERENCES item_template (id),
    quantity_wanted  INT         NOT NULL CHECK (quantity_wanted BETWEEN 1 AND 999),
    quantity_filled  INT         NOT NULL DEFAULT 0,
    unit_price       BIGINT      NOT NULL CHECK (unit_price BETWEEN 1 AND 10000000),
    status           VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'FILLED', 'CANCELLED', 'EXPIRED')),
    created_at       TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    closed_at        TIMESTAMPTZ,
    refunded         BOOLEAN     NOT NULL DEFAULT FALSE,
    CHECK (quantity_filled BETWEEN 0 AND quantity_wanted)
);

CREATE INDEX idx_buy_order_active ON market_buy_order (unit_price DESC, id) WHERE status = 'ACTIVE';
CREATE INDEX idx_buy_order_active_item ON market_buy_order (item_template_id, unit_price DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_buy_order_buyer_active ON market_buy_order (buyer_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_buy_order_expiry ON market_buy_order (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_buy_order_unrefunded ON market_buy_order (id) WHERE status = 'EXPIRED' AND NOT refunded;
