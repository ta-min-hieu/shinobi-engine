-- V6 — chỉ số dẫn xuất tính lại khi đổi trang bị (Phase 5 lát cắt 3, GAMEPLAY.md §2/§7).
--
-- Chỉ 1 chỉ số duy nhất ở lát cắt này (ATK) — GDD §5 còn SKILL_DMG/DEF/RES/... nhưng chưa item
-- nào cần, thêm cột khi có nhu cầu thật (feature-inventory/CLAUDE.md, cùng tinh thần ItemTemplate).
ALTER TABLE item_template ADD COLUMN bonus_atk INT NOT NULL DEFAULT 0;

-- wooden_sword (seed V5) giờ có bonus thật để test end-to-end "mặc đồ → ATK tăng".
UPDATE item_template SET bonus_atk = 5 WHERE code = 'wooden_sword';
