-- V5 — mặc đồ (Phase 5 lát cắt đầu, DATABASE.md §2.1/§2.2, GAMEPLAY.md §7).
--
-- equip_slot NULL = item KHÔNG mặc được (VD "kunai" — vật phẩm tiêu hao, đã seed ở V4).
ALTER TABLE item_template ADD COLUMN equip_slot VARCHAR(16);

-- Không có cột "id" riêng — khoá chính (character_id, slot_type), mỗi nhân vật tối đa 7 dòng
-- (EquipmentSlotType). inventory_item_id NULL nghĩa là ĐANG GỠ slot đó (Equipment aggregate coi
-- "vắng mặt trong map" và "NULL" là cùng 1 trạng thái — feature-inventory đọc lọc IS NOT NULL).
-- ON DELETE SET NULL: nếu item bị xoá khỏi inventory_item (chưa có UseCase nào làm việc này ở
-- lát cắt này, nhưng tương lai sẽ có) mà đang mặc, tự gỡ ra thay vì để FK vi phạm.
CREATE TABLE equipment_slot (
    character_id BIGINT NOT NULL REFERENCES character (id),
    slot_type VARCHAR(16) NOT NULL,
    inventory_item_id BIGINT REFERENCES inventory_item (id) ON DELETE SET NULL,
    PRIMARY KEY (character_id, slot_type)
);

-- Seed 1 vũ khí mặc được để test thật (giống kunai seed ở V4).
INSERT INTO item_template (code, name_key, max_stack, equip_slot) VALUES ('wooden_sword', 'item.wooden_sword.name', 1, 'WEAPON');
