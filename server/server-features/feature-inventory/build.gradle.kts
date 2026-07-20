// feature-inventory — túi đồ nhân vật + rơi đồ (GAMEPLAY.md §5, §7).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, KHÔNG dependency nào khác
// trong repo. Implementation thật (PostgresInventoryAdapter) nằm ở server-database — cùng lý do
// đã ghi trong feature-account/CLAUDE.md mục "Khác template chuẩn".
plugins {
    id("kage.quality-conventions")
}
