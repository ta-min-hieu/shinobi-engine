// feature-trade — trao đổi vật phẩm/gold giữa 2 người chơi (Phase 7 lát cắt 3, GAMEPLAY.md §8 —
// "hệ thống nguy hiểm nhất"). ConfirmTradeUseCase cần TransactionRunner riêng (giống feature-shop/
// feature-mail) vì thi hành chạm NHIỀU bảng khác feature (inventory_item, character_stat) trong
// CÙNG 1 transaction — khác feature-guild/feature-party chỉ chạm bảng của chính mình.
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
