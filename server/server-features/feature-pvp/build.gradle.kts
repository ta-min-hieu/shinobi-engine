// feature-pvp — Arena 1v1 + MMR/rating cơ bản (Phase 8 lát cắt 4, GDD §6). Chỉ lo PHẦN CẦN
// PERSIST (rating/wins/losses) — vòng đời trận đấu (challenge/world instance) KHÔNG có dữ liệu
// nghiệp vụ cần lưu, đặt ở server-gateway (ArenaInstanceRegistry), cùng lý do
// DungeonInstanceRegistry KHÔNG phải feature-dungeon (xem ADR-026 + ROADMAP Phase 8 lát cắt 1).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
