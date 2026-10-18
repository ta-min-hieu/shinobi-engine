-- V60 — index đọc cho Bia Anh Danh (ADR-057, chủ dự án duyệt 2026-10-18, lô 15). CHỈ thêm index,
-- không đổi bảng nào. Bảng Lực Chiến không có index: con số được tính ở Java (ADR-056), không có cột.
CREATE INDEX IF NOT EXISTS idx_character_realm_rank ON character (level DESC, exp DESC, id);
CREATE INDEX IF NOT EXISTS idx_pvp_rating_rank ON pvp_rating (rating DESC, character_id);
