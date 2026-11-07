-- ADR-189 (2026-11-07) — Lịch sử giao dịch. KHÔNG thêm bảng: mỗi lần giao dịch thành công đã ghi CHO MỖI BÊN một dòng
-- audit_log 'TRADE_EXECUTED' (đưa gì, nhận gì, với ai — sửa 2026-10-03). Chỉ thêm chỉ mục một phần để đọc 30 ngày gần
-- nhất của một nhân vật không quét cả bảng audit.
CREATE INDEX idx_audit_log_trade ON audit_log (character_id, created_at DESC) WHERE action = 'TRADE_EXECUTED';
