-- ADR-239 (2026-11-08) — buff bang: hạn +EXP của cả bang (NULL = chưa từng bật).
ALTER TABLE guild ADD COLUMN exp_buff_until TIMESTAMPTZ;

-- ADR-240 (2026-11-08) — kỷ lục cá nhân: một dòng mỗi (nhân vật, loại); chỉ nâng lên, không bao giờ hạ.
CREATE TABLE personal_record (
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    kind         VARCHAR(32) NOT NULL,
    value        BIGINT      NOT NULL CHECK (value >= 0),
    achieved_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, kind)
);

-- ADR-241 (2026-11-08) — bảng giàu có đọc linh thạch giảm dần.
CREATE INDEX idx_character_stat_gold ON character_stat (gold DESC) WHERE gold > 0;
