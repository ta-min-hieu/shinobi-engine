-- ADR-198 — kho bang: thành viên gửi đồ vào kho chung của bang; bang chủ / phó bang chủ rút. Bảng RIÊNG theo guild_id
-- (cùng lý do storage_item ở V63). Bang giải tán ⇒ kho đi theo (ON DELETE CASCADE). Sức chứa là hằng số
-- StoragePolicy.CAPACITY như kho tông môn.
CREATE TABLE guild_storage_item (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id          BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    item_template_id  BIGINT      NOT NULL REFERENCES item_template (id),
    quantity          INT         NOT NULL CHECK (quantity >= 1),
    enhance_level     INT         NOT NULL DEFAULT 0 CHECK (enhance_level >= 0),
    stored_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guild_storage_item_guild_id ON guild_storage_item (guild_id);

-- Mỗi lần gửi / rút là một dòng nhật ký bang (amount = số lượng, detail = mã vật phẩm).
ALTER TABLE guild_log DROP CONSTRAINT guild_log_kind_check;
ALTER TABLE guild_log ADD CONSTRAINT guild_log_kind_check CHECK (kind IN ('CREATED', 'JOINED', 'LEFT', 'KICKED',
    'DONATED', 'LEVEL_UP', 'RENAMED', 'NOTICE_CHANGED', 'LEADER_CHANGED', 'VICE_APPOINTED', 'VICE_DISMISSED',
    'BANK_DEPOSIT', 'BANK_WITHDRAW'));
