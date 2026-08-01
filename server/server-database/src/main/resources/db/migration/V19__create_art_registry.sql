-- ART-PIPELINE.md §8.2 — đăng ký toàn bộ asset đồ họa để phục vụ trang quản trị (yêu cầu chủ dự án
-- 2026-08-01). DB lưu METADATA (đường dẫn, trạng thái duyệt, license), KHÔNG lưu binary — file thật
-- vẫn nằm trên đĩa, phục vụ qua nginx (§8.4). tools:asset-pipeline (ADR-029) sẽ upsert bảng
-- art_asset ở bước cuối cùng của pipeline khi bước đó được nối thêm (chưa làm ở v1 — ART-PIPELINE
-- §12 mục 3/4).
--
-- Danh mục 2 tầng thay vì 1 cột enum: asset luôn gắn vào tầng LÁ (tầng 2), tầng 1 chỉ để gom
-- nhóm/lọc trên trang quản trị — thêm loại asset mới sau này chỉ cần thêm 1 dòng art_category,
-- không cần migration.
CREATE TABLE art_category (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(32) NOT NULL UNIQUE,
    name_vi    VARCHAR(64) NOT NULL,
    parent_id  BIGINT REFERENCES art_category (id),
    sort_order SMALLINT NOT NULL DEFAULT 0
);

-- Liên kết gameplay là soft-link (asset_key <-> item_template.code/skill_code...), KHÔNG FK cứng —
-- asset có thể được vẽ trước hoặc sau khi nội dung gameplay tương ứng tồn tại (§8.3).
CREATE TABLE art_asset (
    id          BIGSERIAL PRIMARY KEY,
    asset_key   VARCHAR(64)  NOT NULL UNIQUE,
    category_id BIGINT       NOT NULL REFERENCES art_category (id),
    element     VARCHAR(8),
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    -- Đường dẫn: mỗi asset có thể nằm trong NHIỀU atlas (1 cho mỗi tier low/med/high — ADR-028),
    -- nên chỉ lưu 4 mốc, đường dẫn phát hành từng tier SUY RA theo quy ước (§8.5), không lưu cứng.
    blend_path  VARCHAR(255),
    render_dir  VARCHAR(255),
    preview_url VARCHAR(255),
    atlas_name  VARCHAR(64),
    actions     JSONB,
    master_px   SMALLINT,
    license     VARCHAR(16)  NOT NULL DEFAULT 'ORIGINAL',
    attribution VARCHAR(255),
    checksum    VARCHAR(80),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_art_asset_category ON art_asset (category_id, status);

-- Cây danh mục seed sẵn (ART-PIPELINE.md §8.2 bảng "Cây danh mục seed") — admin chỉnh sau qua
-- trang quản trị (thêm/sửa tầng 2 không cần migration), đây chỉ là điểm khởi đầu.
INSERT INTO art_category (code, name_vi, sort_order) VALUES
    ('character', 'Nhân vật', 0),
    ('monster', 'Quái', 1),
    ('vfx', 'Hiệu ứng', 2),
    ('icon', 'Icon', 3),
    ('environment', 'Môi trường', 4),
    ('ui', 'Giao diện', 5);

INSERT INTO art_category (code, name_vi, parent_id, sort_order) VALUES
    ('character-player', 'Nhân vật người chơi', (SELECT id FROM art_category WHERE code = 'character'), 0),
    ('character-npc', 'NPC', (SELECT id FROM art_category WHERE code = 'character'), 1),
    ('monster-normal', 'Quái thường', (SELECT id FROM art_category WHERE code = 'monster'), 0),
    ('monster-elite', 'Quái tinh anh', (SELECT id FROM art_category WHERE code = 'monster'), 1),
    ('monster-boss', 'Boss', (SELECT id FROM art_category WHERE code = 'monster'), 2),
    ('monster-world-boss', 'World Boss', (SELECT id FROM art_category WHERE code = 'monster'), 3),
    ('vfx-skill', 'VFX skill', (SELECT id FROM art_category WHERE code = 'vfx'), 0),
    ('vfx-hit', 'VFX trúng đòn', (SELECT id FROM art_category WHERE code = 'vfx'), 1),
    ('vfx-aura-buff', 'VFX buff/aura', (SELECT id FROM art_category WHERE code = 'vfx'), 2),
    ('vfx-ambient', 'VFX môi trường', (SELECT id FROM art_category WHERE code = 'vfx'), 3),
    ('icon-item', 'Icon vật phẩm', (SELECT id FROM art_category WHERE code = 'icon'), 0),
    ('icon-skill', 'Icon skill', (SELECT id FROM art_category WHERE code = 'icon'), 1),
    ('icon-buff', 'Icon buff', (SELECT id FROM art_category WHERE code = 'icon'), 2),
    ('env-tile', 'Tile môi trường', (SELECT id FROM art_category WHERE code = 'environment'), 0),
    ('env-prop', 'Prop môi trường', (SELECT id FROM art_category WHERE code = 'environment'), 1),
    ('env-parallax', 'Parallax nền', (SELECT id FROM art_category WHERE code = 'environment'), 2),
    ('ui-frame', 'Khung UI', (SELECT id FROM art_category WHERE code = 'ui'), 0),
    ('ui-button', 'Nút UI', (SELECT id FROM art_category WHERE code = 'ui'), 1),
    ('ui-misc', 'UI khác', (SELECT id FROM art_category WHERE code = 'ui'), 2);
