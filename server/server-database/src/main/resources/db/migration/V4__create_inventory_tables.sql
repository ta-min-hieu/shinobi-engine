-- V4 — bảng item_template/inventory_item/drop_table (DATABASE.md §"Bảng tĩnh/động", feature-
-- inventory/CLAUDE.md) — Phase 4 lát cắt "rơi đồ thật vào túi đồ" (mở sớm hơn ROADMAP dự kiến
-- Phase 5, quyết định của chủ dự án).
--
-- ⚠️ Khác DATABASE.md: drop_table.entries_json (JSON blob) được NORMALIZE thành bảng quan hệ
-- drop_table_entry (1 dòng = 1 entry) thay vì cột JSON — dự án chưa có thư viện JSON
-- (Jackson/Gson) trong TECHNOLOGY-STACK.md, thêm mới cần dừng hỏi (CLAUDE.md §4). Cùng đạt mục
-- đích "config, không code" mà không cần dependency mới. Xem feature-inventory/CLAUDE.md.
--
-- Không có bảng "inventory" riêng — Inventory (aggregate) lắp từ các dòng inventory_item hiện có
-- (có thể rỗng) + capacity hằng số phía code, không phải cột DB.
CREATE TABLE item_template (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name_key VARCHAR(64) NOT NULL,
    max_stack INT NOT NULL DEFAULT 1
);

CREATE TABLE inventory_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES character (id),
    item_template_id BIGINT NOT NULL REFERENCES item_template (id),
    quantity INT NOT NULL,
    enhance_level INT NOT NULL DEFAULT 0,
    slot_index INT NOT NULL
);

CREATE INDEX idx_inventory_item_character_id ON inventory_item (character_id);

CREATE TABLE drop_table (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE drop_table_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drop_table_id BIGINT NOT NULL REFERENCES drop_table (id),
    item_template_id BIGINT NOT NULL REFERENCES item_template (id),
    drop_chance_percent INT NOT NULL,
    min_quantity INT NOT NULL,
    max_quantity INT NOT NULL
);

CREATE INDEX idx_drop_table_entry_drop_table_id ON drop_table_entry (drop_table_id);

-- Seed tối thiểu để lát cắt AI/Combat có gì đó thật để rơi — monster_template chưa tồn tại (quái
-- vẫn hardcode "training_dummy" phía Java), nên drop_table.code khớp thẳng monsterCode thay vì
-- qua monster_template.drop_table_id (đó là bước tổng quát hoá của lát cắt monster_template sau).
INSERT INTO item_template (code, name_key, max_stack) VALUES ('kunai', 'item.kunai.name', 99);

INSERT INTO drop_table (code) VALUES ('training_dummy');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 50, 1, 3
  FROM drop_table dt, item_template it
 WHERE dt.code = 'training_dummy' AND it.code = 'kunai';
