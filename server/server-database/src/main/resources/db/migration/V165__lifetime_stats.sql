-- ADR-223 (2026-11-08) — thống kê cá nhân: số lần bị hạ và tổng giây trong world, cộng dồn suốt đời nhân vật.
-- (Số quái đã hạ / nhiệm vụ đã trả lấy từ bộ đếm thành tựu V64; thắng/thua Luận Kiếm từ pvp_rating.)
CREATE TABLE character_lifetime_stats (
    character_id     BIGINT PRIMARY KEY REFERENCES character (id),
    deaths           INT    NOT NULL DEFAULT 0 CHECK (deaths >= 0),
    playtime_seconds BIGINT NOT NULL DEFAULT 0 CHECK (playtime_seconds >= 0)
);
