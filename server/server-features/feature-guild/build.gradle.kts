// feature-guild — bang hội (Phase 7 lát cắt 1, GAMEPLAY.md §11). createGuild/approveJoinRequest
// chạm 2 bảng nguyên tử NHƯNG cả 2 bảng đều thuộc feature này (không xuyên feature khác) — nên
// KHÔNG cần TransactionRunner riêng như feature-shop/feature-quest, adapter tự mở transaction thủ
// công (xem GuildPort javadoc).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
