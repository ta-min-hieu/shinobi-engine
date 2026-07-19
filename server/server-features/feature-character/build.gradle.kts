// feature-character — tạo/liệt kê nhân vật (GAMEPLAY.md §2).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, KHÔNG dependency nào khác
// trong repo. Implementation thật (PostgresCharacterAdapter) nằm ở server-database — cùng lý do
// đã ghi trong feature-account/CLAUDE.md mục "Khác template chuẩn" (ArchUnit chặn JDBC theo
// package dev.kage.server.database, không theo Gradle module).
plugins {
    id("kage.quality-conventions")
}
