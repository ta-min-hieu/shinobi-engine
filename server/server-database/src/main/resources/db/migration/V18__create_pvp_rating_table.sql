-- Phase 8 lát cắt 4 — Arena 1v1 + MMR/rating cơ bản (feature-pvp/CLAUDE.md). 1 dòng/nhân vật,
-- chỉ tạo lúc trận đầu tiên kết thúc (UPSERT), không tạo sẵn lúc character được tạo.
CREATE TABLE pvp_rating (
    character_id BIGINT PRIMARY KEY REFERENCES character (id),
    rating INT NOT NULL,
    wins INT NOT NULL,
    losses INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
