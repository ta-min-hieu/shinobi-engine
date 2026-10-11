-- V52 — hàng rào DB cho bốn con số mà tới giờ CHỈ code giữ (chủ dự án duyệt 2026-10-11; docs/README.md
-- dòng 74(b), 75(b), 77(d)). Chỉ thêm CHECK: không thêm/xoá cột, không đổi dữ liệu, không đổi protocol.
--
-- VÌ SAO: cả bốn đều đã có hậu quả ĐO ĐƯỢC trên Postgres thật khi code lỡ để lọt một giá trị xấu, và
-- hậu quả nào cũng nặng hơn nhiều so với một lượt ghi bị từ chối:
--
-- * `character.exp < 0` — `Character` từ chối exp âm ngay trong constructor ⇒ `findById` VÀ
--   `listByAccount` cùng ném: nhân vật không vào được world, cả tài khoản không liệt kê nổi nhân vật.
--   (2026-10-10: `ctx.give_exp(số âm)` từng làm đúng việc đó; nay bị chặn ở tầng script.)
-- * `inventory_item.quantity <= 0` — `ItemStack` từ chối số lượng ≤ 0 trong constructor ⇒ túi đồ của
--   nhân vật không đọc lên được nữa. (2026-10-10: `ctx.give_item(code, số âm)` để lại `quantity = -2`.)
-- * `character_stat.gold < 0` — vàng âm là nợ hệ thống không ai đòi được; mọi đường trừ vàng đều tự
--   kiểm số dư trước, nhưng không gì ở DB bắt chúng phải thế.
-- * `trade_session.requester_gold/target_gold < 0` — vàng đặt lên bàn giao dịch âm là rút vàng của
--   BÊN KIA lúc chốt.
--
-- `mail.gold_amount`, `mail_attachment.quantity`, `trade_session_item.quantity`, `shop_item.*_price`
-- đã có CHECK từ lúc tạo bảng (V8/V13/V16) — file này lấp đúng những cột còn hở, không lặp lại.
--
-- HỆ QUẢ LÚC CHẠY: một đường code lỡ ghi giá trị xấu nay nhận lỗi SQL và transaction của nó rollback
-- trọn, thay vì commit một dòng làm hỏng nhân vật. Đó là đổi một lỗi IM (dữ liệu hỏng, lộ ra muộn) lấy
-- một lỗi ỒN (lượt đó thất bại, có log).
--
-- ⚠️ DB đang có sẵn dòng vi phạm thì file này KHÔNG chạy được và server không khởi động — cố ý: dòng
-- như thế là nhân vật/túi đồ đang hỏng, phải sửa tay trước chứ không được lặng lẽ bỏ qua. DB dev đã
-- soi 2026-10-11: 0 dòng vi phạm ở cả bốn bảng.
ALTER TABLE character
    ADD CONSTRAINT character_exp_non_negative CHECK (exp >= 0);

ALTER TABLE character_stat
    ADD CONSTRAINT character_stat_gold_non_negative CHECK (gold >= 0);

ALTER TABLE inventory_item
    ADD CONSTRAINT inventory_item_quantity_positive CHECK (quantity > 0);

ALTER TABLE trade_session
    ADD CONSTRAINT trade_session_gold_non_negative CHECK (requester_gold >= 0 AND target_gold >= 0);
