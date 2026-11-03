-- ADR-159 (2026-11-03) — Khoá vật phẩm. Món người chơi đã khoá bị chặn ở MỌI đường làm nó rời nhân vật hoặc bị đổi lấy
-- thứ khác (bán NPC, giao dịch, thư, phường thị + lệnh mua, kho tông môn, phân giải, nộp nhiệm vụ, nguyên liệu lò đan)
-- — y như món đang mặc. Dùng trực tiếp (đan, phù, khảm ngọc) vẫn được. Mặc định chưa khoá: dòng cũ không đổi hành vi.
ALTER TABLE inventory_item ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
