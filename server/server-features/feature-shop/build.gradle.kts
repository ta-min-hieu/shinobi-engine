// feature-shop — mua/bán item với NPC ảo (chưa có NPC/vị trí thật, Phase 6 mới có — GAMEPLAY.md §8).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, KHÔNG dependency nào khác
// trong repo. Implementation thật (PostgresShop*Adapter) nằm ở server-database — cùng lý do đã
// ghi trong feature-account/CLAUDE.md mục "Khác template chuẩn".
plugins {
    id("kage.quality-conventions")
}
