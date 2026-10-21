-- ADR-071 — bậc chiêu thức (điểm công pháp). Chỉ lưu chiêu ĐÃ nâng (bậc 2–5); vắng dòng = bậc 1. Điểm KHÔNG
-- lưu: tổng = cấp / 2, đã tiêu = Σ(bậc − 1) trên chiêu của linh căn hiện tại.
CREATE TABLE character_skill_rank (
    character_id  BIGINT      NOT NULL REFERENCES character (id),
    skill_code    VARCHAR(64) NOT NULL,
    rank          INT         NOT NULL CHECK (rank BETWEEN 1 AND 5),
    PRIMARY KEY (character_id, skill_code)
);
