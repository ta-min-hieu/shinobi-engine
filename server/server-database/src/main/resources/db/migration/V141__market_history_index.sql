-- ADR-177 (2026-11-06) — Lịch sử phường thị. KHÔNG thêm cột: market_listing đã có seller_id/buyer_id/closed_at/status.
-- Chỉ thêm hai chỉ mục một phần để "món tôi đã bán" / "món tôi đã mua" (30 ngày, mới nhất trước) không quét cả bảng.
CREATE INDEX idx_market_listing_seller_sold ON market_listing (seller_id, closed_at DESC) WHERE status = 'SOLD';
CREATE INDEX idx_market_listing_buyer_sold ON market_listing (buyer_id, closed_at DESC) WHERE status = 'SOLD';
