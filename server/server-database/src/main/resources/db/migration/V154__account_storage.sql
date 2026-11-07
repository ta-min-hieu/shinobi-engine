-- ADR-197 — kho chung tài khoản: mọi nhân vật cùng tài khoản gửi / rút chung một kho, ở cùng người giữ kho tông môn.
-- Bảng RIÊNG theo account_id (cùng lý do storage_item ở V63 không thêm cột vào inventory_item). Sức chứa là hằng số
-- StoragePolicy.CAPACITY như kho tông môn.
CREATE TABLE account_storage_item (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id        BIGINT      NOT NULL REFERENCES account (id),
    item_template_id  BIGINT      NOT NULL REFERENCES item_template (id),
    quantity          INT         NOT NULL CHECK (quantity >= 1),
    enhance_level     INT         NOT NULL DEFAULT 0 CHECK (enhance_level >= 0),
    stored_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_account_storage_item_account_id ON account_storage_item (account_id);
