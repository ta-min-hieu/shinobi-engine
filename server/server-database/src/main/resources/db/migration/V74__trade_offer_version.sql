-- ADR-075 (lô 18, chủ dự án duyệt 2026-10-23): phiên bản lời chào của một phiên giao dịch. Mỗi lần một bên
-- đặt lại lời chào thì tăng 1 (PostgresTradeSessionAdapter#replaceOffer); xác nhận mang phiên bản mình đã thấy,
-- lệch thì bị từ chối dưới khoá thi hành. DEFAULT 1 — phiên đang mở lúc migrate nhận version 1; client cũ gửi 0
-- (bỏ qua phép so).
ALTER TABLE trade_session ADD COLUMN offer_version BIGINT NOT NULL DEFAULT 1;
