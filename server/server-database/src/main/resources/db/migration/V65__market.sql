-- ADR-063 — phường thị: treo bán món CHƯA cường hoá lấy linh thạch, phí 5%, treo tối đa 48 giờ.
--
-- Món treo đã RỜI túi người bán (ký quỹ, D2) nên không có enhance_level ở đây: chỉ món +0 được treo, cùng
-- luật thư đính kèm. Vòng đời: ACTIVE → SOLD | CANCELLED | EXPIRED; EXPIRED rồi mới gửi thư trả, đánh
-- dấu returned (D4). Mọi chuyển trạng thái là compare-and-set trên status.

CREATE TABLE market_listing (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id         BIGINT      NOT NULL REFERENCES character (id),
    item_template_id  BIGINT      NOT NULL REFERENCES item_template (id),
    quantity          INT         NOT NULL CHECK (quantity >= 1),
    price             BIGINT      NOT NULL CHECK (price BETWEEN 1 AND 10000000),
    status            VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'SOLD', 'CANCELLED', 'EXPIRED')),
    listed_at         TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    buyer_id          BIGINT      REFERENCES character (id),
    closed_at         TIMESTAMPTZ,
    returned          BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Xem bảng tin: dòng đang bán, mới nhất trước (lọc theo loại tuỳ chọn).
CREATE INDEX idx_market_listing_active ON market_listing (listed_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_market_listing_active_item ON market_listing (item_template_id, listed_at DESC)
    WHERE status = 'ACTIVE';
-- Trần 10 dòng/người + "của tôi".
CREATE INDEX idx_market_listing_seller_active ON market_listing (seller_id) WHERE status = 'ACTIVE';
-- Tác vụ hết hạn: dòng quá hạn chưa đóng, và dòng đã hết hạn chưa trả.
CREATE INDEX idx_market_listing_expiry ON market_listing (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_market_listing_unreturned ON market_listing (id) WHERE status = 'EXPIRED' AND NOT returned;
