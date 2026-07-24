// feature-party — nhóm chơi chung (Phase 7 lát cắt 2, GAMEPLAY.md §11 mở rộng). createParty/
// acceptInvite/leaveParty chạm nhiều bảng nguyên tử NHƯNG cả 3 bảng đều thuộc feature này —
// KHÔNG cần TransactionRunner riêng như feature-shop/feature-quest, adapter tự mở transaction thủ
// công (xem PartyPort javadoc).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
