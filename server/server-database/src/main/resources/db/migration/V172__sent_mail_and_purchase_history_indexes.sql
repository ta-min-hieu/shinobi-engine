-- V172 — lô 45 (ADR-288, ADR-290). Chỉ thêm chỉ mục, không đổi bảng.
-- ADR-290 thư đã gửi: đọc mail theo sender_id, mới trước (thư hệ thống sender_id NULL không cần).
CREATE INDEX idx_mail_sender ON mail (sender_id, id DESC) WHERE sender_id IS NOT NULL;
-- ADR-288 lịch sử mua cửa hàng NPC: audit 'SHOP_BUY' (BuyItemUseCase ghi sẵn từ Phase 5).
CREATE INDEX idx_audit_log_shop_buy ON audit_log (character_id, id DESC) WHERE action = 'SHOP_BUY';
