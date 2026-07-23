// feature-friend — kết bạn (Phase 6 lát cắt 4, GAMEPLAY.md §11). Không có gold/item nên KHÔNG cần
// Transaction/TransactionRunner riêng (khác feature-shop/feature-quest) — mọi thao tác Port là 1
// câu SQL đơn.
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
