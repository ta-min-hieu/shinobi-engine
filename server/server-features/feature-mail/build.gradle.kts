// feature-mail — thư có đính kèm gold/item (Phase 6 lát cắt 5, GAMEPLAY.md §11). Đính kèm item =
// con đường duplicate item kinh điển (nhận nhưng không đánh dấu đã nhận -> nhận lại được) — bắt
// buộc transaction khi nhận, cùng mẫu feature-shop/feature-quest.
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
