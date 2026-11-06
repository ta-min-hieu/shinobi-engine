-- ADR-175 (2026-11-06) — Lưu bộ trang bị. Mỗi nhân vật tối đa 3 bộ; một dòng = một ô trong bộ trỏ tới MỘT món cụ thể.
-- KHÔNG khoá ngoại tới inventory_item: món đã bán/phân giải thì dòng ở lại, lúc mặc bộ server bỏ qua và báo "thiếu".
CREATE TABLE equipment_preset (
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    preset_no    SMALLINT    NOT NULL CHECK (preset_no BETWEEN 1 AND 3),
    slot         VARCHAR(16) NOT NULL,
    item_id      BIGINT      NOT NULL,
    PRIMARY KEY (character_id, preset_no, slot)
);
