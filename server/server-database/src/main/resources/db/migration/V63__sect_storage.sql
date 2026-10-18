-- ADR-061 — kho đồ tông môn: 40 dòng mỗi nhân vật (hằng số StoragePolicy.CAPACITY), gửi/rút ở Tô Vân Nhi.
--
-- Bảng RIÊNG, không thêm cột vị trí vào inventory_item: mọi câu đếm/khoá túi đồ hiện có không phải sửa
-- và không thể vô tình đếm đồ trong kho (D1). Chuyển túi ↔ kho là một transaction của feature-inventory,
-- dưới khoá túi đồ của nhân vật (DATABASE.md §5.1).

CREATE TABLE storage_item (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id      BIGINT      NOT NULL REFERENCES character (id),
    item_template_id  BIGINT      NOT NULL REFERENCES item_template (id),
    quantity          INT         NOT NULL CHECK (quantity >= 1),
    enhance_level     INT         NOT NULL DEFAULT 0 CHECK (enhance_level >= 0),
    stored_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_storage_item_character_id ON storage_item (character_id);
