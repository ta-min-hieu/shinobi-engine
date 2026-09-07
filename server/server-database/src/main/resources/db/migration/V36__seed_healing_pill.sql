-- V36 — đan dược hồi máu (ADR-046 D5): hai cột mới trên `item_template` + món đầu tiên dùng chúng.
--
-- ============================================================================================
-- VÌ SAO BÂY GIỜ MỚI CÓ — chính V21 đã nói trước điều kiện
-- ============================================================================================
-- V21 §3 viết nguyên văn:
--
--     "CỐ Ý KHÔNG thêm thuốc hồi máu / vật phẩm 'dùng được': server CHƯA có UseItemUseCase nào —
--      thêm bình máu bây giờ là nội dung chết, người chơi bấm không có gì xảy ra."
--
-- Điều kiện đó vừa hết hiệu lực: ADR-046 D5 dựng `UseItemUseCase` thật, opcode 511/512 thật, và
-- nút "Dùng" thật trong túi đồ. Ghi chú ở V21 KHÔNG bị xoá (migration đã chạy là bất biến) — dòng
-- này là chỗ trả lời nó.
--
-- ============================================================================================
-- HAI CỘT, KHÔNG PHẢI MỘT BẢNG RIÊNG
-- ============================================================================================
-- `use_effect` NULL = món KHÔNG dùng được, tức trạng thái của MỌI hàng đang có (trang bị, nguyên
-- liệu, vật phẩm nhiệm vụ). Không cần backfill, không cần DEFAULT giả.
--
-- Bảng riêng `item_use_effect` sẽ đúng khi một món có NHIỀU hiệu ứng cùng lúc (hồi máu + giải độc
-- + buff). Hiện chưa món nào như thế, và một bảng 1-1 chỉ để "cho tổng quát" là một JOIN thêm ở
-- mọi lần tra túi đồ — đường nóng nhất của feature-inventory.
--
-- Chuỗi trong `use_effect` khớp enum Java `ItemUseEffect`. Giá trị LẠ (gõ nhầm ở migration sau)
-- được adapter coi là "không dùng được" chứ KHÔNG làm sập đường nạp túi — cùng luật ADR-037 áp cho
-- `element` lạ của quái.
--
-- ============================================================================================
-- CÂN BẰNG: vì sao 50 máu / 80 vàng / chồng 20
-- ============================================================================================
-- * **50 máu** ≈ 42% máu tối đa của nhân vật cấp 6 mặc bộ Tạp Dịch (118). Đủ để cứu một trận sát
--   nút, KHÔNG đủ để biến trận đấu thành cuộc thi bấm nút: thạch vệ đánh 20/nhát, nên một viên
--   mua thêm đúng 2 nhát rưỡi.
-- * **80 vàng** đặt giữa `novice_headband` (60) và `novice_vest` (120) — cùng bậc với đồ Tập Sự,
--   tức một người chơi arc 1 mua được vài viên mà không bỏ được việc mua giáp.
-- * **Chồng 20** theo đúng luật của chủ dự án đã khoá trong `ItemTemplate`: mặc được thì
--   `max_stack = 1`, tiêu hao thì > 1. 20 chứ không phải 99 vì túi chỉ có 30 ô và một chồng 99
--   viên đan là "không bao giờ phải nghĩ tới hồi máu nữa".
-- * `sell_price` 20 = 1/4 giá mua, đúng tỉ lệ mọi hàng shop_item đang dùng.
--
-- Đây là món DUY NHẤT của đợt này: bậc cao hơn là nội dung của arc 2, và một món thứ hai bây giờ
-- chỉ làm loãng phép đo cân bằng đầu tiên.
-- ============================================================================================
ALTER TABLE item_template
    ADD COLUMN use_effect VARCHAR(32),
    ADD COLUMN use_value INT NOT NULL DEFAULT 0;

-- Hàng rào song song với compact constructor của `ItemTemplate`: món mặc được thì không dùng được
-- (dùng xong là mất, mà nó đang nằm trên người), và món dùng được phải có giá trị > 0 để làm gì đó.
ALTER TABLE item_template
    ADD CONSTRAINT item_template_usable_is_not_equippable
        CHECK (use_effect IS NULL OR equip_slot IS NULL),
    ADD CONSTRAINT item_template_usable_needs_value
        CHECK (use_effect IS NULL OR use_value > 0);

INSERT INTO item_template (code, name_key, max_stack, use_effect, use_value)
SELECT 'minor_healing_pill', 'item.minor_healing_pill.name', 20, 'HEAL_HP', 50
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'minor_healing_pill');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 80, 20 FROM item_template WHERE code = 'minor_healing_pill';
