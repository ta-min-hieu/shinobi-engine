-- V22 — ADR-036 (Accepted 2026-08-30): trang bị phải có chỉ số THẬT, và cường hoá phải có tác dụng.
--
-- Trước migration này `item_template` có ĐÚNG MỘT cột chỉ số (`bonus_atk`, V6). Hệ quả đo được:
--   * 6/7 ô trang bị vô nghĩa — V21 buộc phải cho áo giáp/mũ/găng/giày cộng ATK để chúng không
--     thành nội dung chết (xem cảnh báo đầu file V21).
--   * `required_level` mà GAMEPLAY.md §7 mô tả KHÔNG tồn tại vì không có cột ⇒ nhân vật Cấp 1 đủ
--     vàng là mặc trọn bộ "Hạ Nhẫn".
--
-- Chỉ THÊM cột có DEFAULT — không đổi cột đang chạy, không backfill, dữ liệu cũ vẫn hợp lệ nguyên
-- trạng (ADR-036 D1). Phần 2 chỉnh lại SỐ LIỆU 15 món của V21 cho đúng chỗ; đó là dữ liệu, không
-- phải schema — cân bằng sau này chỉnh ở migration mới, không đụng code (Điều 27 "DB = tham số").

-- ---------------------------------------------------------------------------------------------
-- 1. Schema — 4 cột mới (ADR-036 D1).
--
--    KHÔNG thêm `required_faction`: 15 món của V21 đều là đồ chung, một cột chỉ toàn NULL là một
--    cột chưa cần. Thêm khi có bộ đồ riêng hệ phái đầu tiên (Revisit Trigger của ADR-036).
-- ---------------------------------------------------------------------------------------------
ALTER TABLE item_template ADD COLUMN bonus_def INT NOT NULL DEFAULT 0;
ALTER TABLE item_template ADD COLUMN bonus_res INT NOT NULL DEFAULT 0;
ALTER TABLE item_template ADD COLUMN bonus_hp INT NOT NULL DEFAULT 0;
ALTER TABLE item_template ADD COLUMN required_level INT NOT NULL DEFAULT 1;

-- ---------------------------------------------------------------------------------------------
-- 2. Cân bằng lại 15 món của V21 + 1 món của V5 — chỉ số về ĐÚNG chỗ của nó.
--
--    Mốc so sánh, nhân vật Cấp 1 chỉ số nền 10/10/10/10 (CombatConstants.deriveCombatStats):
--      ATK 30 · SKILL_DMG 25 · DEF 20 · RES 15 · HP 100 (CHARACTER_MAX_HP, còn là placeholder).
--
--    Nguyên tắc đặt số:
--      * VŨ KHÍ cộng ATK, KHÔNG cộng phòng thủ. GIÁP/MŨ/GIÀY cộng DEF/RES/HP, KHÔNG cộng ATK.
--        GĂNG là món lai (một ít ATK). TRANG SỨC nghiêng về RES/HP + chút ATK.
--      * Giảm damage kiểu LMHT/Liên Quân: mitigation = 100/(100+DEF) — DEF cộng thêm luôn có ích,
--        không có ngưỡng miễn nhiễm, nên số DEF cùng độ lớn với DEF nền là an toàn.
--      * Trọn bộ "Hạ Nhẫn" (không tính vũ khí): +11 ATK · +23 DEF · +24 RES · +58 HP.
--        Cộng cả steel_katana: ATK 30→59, DEF 20→43, RES 15→39, HP 100→158 — đáng cày, chưa tới
--        mức bộ đồ nuốt hết chỉ số nền.
--      * required_level: "Tập Sự" = 1 (mua được ngay), "Hạ Nhẫn" = 5 (chỗ tiêu vàng đầu tiên,
--        GDD §14 Gold Sink — không được mua xong mặc luôn ở Cấp 1).
--
--    ⚠️ Món ĐANG MẶC không bị gỡ ra khi số `required_level` đổi: phép kiểm chỉ chạy lúc MẶC
--    (EquipItemUseCase). Cố ý — tước đồ khỏi nhân vật đang chơi vì một lần cân bằng là tệ hơn.
-- ---------------------------------------------------------------------------------------------

-- Vũ khí — ATK thuần.
UPDATE item_template SET bonus_atk =  5, required_level = 1 WHERE code = 'wooden_sword';
UPDATE item_template SET bonus_atk = 18, required_level = 5 WHERE code = 'steel_katana';

-- Bậc "Tập Sự" — Cấp 1.
UPDATE item_template SET bonus_atk = 0, bonus_def = 3, bonus_res = 2, bonus_hp =  8, required_level = 1
 WHERE code = 'novice_vest';
UPDATE item_template SET bonus_atk = 0, bonus_def = 2, bonus_res = 1, bonus_hp =  4, required_level = 1
 WHERE code = 'novice_headband';
UPDATE item_template SET bonus_atk = 1, bonus_def = 1, bonus_res = 0, bonus_hp =  2, required_level = 1
 WHERE code = 'novice_wraps';
UPDATE item_template SET bonus_atk = 0, bonus_def = 1, bonus_res = 1, bonus_hp =  2, required_level = 1
 WHERE code = 'novice_sandals';
UPDATE item_template SET bonus_atk = 1, bonus_def = 0, bonus_res = 1, bonus_hp =  2, required_level = 1
 WHERE code = 'novice_bracelet';
UPDATE item_template SET bonus_atk = 1, bonus_def = 0, bonus_res = 2, bonus_hp =  4, required_level = 1
 WHERE code = 'novice_necklace';

-- Bậc "Hạ Nhẫn" — Cấp 5.
UPDATE item_template SET bonus_atk = 0, bonus_def = 10, bonus_res = 6, bonus_hp = 20, required_level = 5
 WHERE code = 'genin_vest';
UPDATE item_template SET bonus_atk = 0, bonus_def = 6, bonus_res = 4, bonus_hp = 10, required_level = 5
 WHERE code = 'genin_headband';
UPDATE item_template SET bonus_atk = 3, bonus_def = 3, bonus_res = 1, bonus_hp =  6, required_level = 5
 WHERE code = 'genin_wraps';
UPDATE item_template SET bonus_atk = 0, bonus_def = 4, bonus_res = 3, bonus_hp =  6, required_level = 5
 WHERE code = 'genin_sandals';
UPDATE item_template SET bonus_atk = 5, bonus_def = 0, bonus_res = 4, bonus_hp =  6, required_level = 5
 WHERE code = 'genin_bracelet';
UPDATE item_template SET bonus_atk = 3, bonus_def = 0, bonus_res = 6, bonus_hp = 10, required_level = 5
 WHERE code = 'genin_necklace';
