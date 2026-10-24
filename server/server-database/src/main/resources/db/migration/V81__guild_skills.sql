-- ADR-085 — kỹ năng bang. Chỉ lưu kỹ năng ĐÃ nâng (bậc 1–4); vắng dòng = bậc 0. Điểm KHÔNG lưu: tổng = cấp bang × 2,
-- đã tiêu = tổng bậc. Bang giải tán ⇒ dòng đi theo.
CREATE TABLE guild_skill (
    guild_id    BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    skill_code  VARCHAR(32) NOT NULL,
    rank        INT         NOT NULL CHECK (rank BETWEEN 1 AND 4),
    PRIMARY KEY (guild_id, skill_code)
);
