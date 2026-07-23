// feature-quest — theo dõi tiến độ + thưởng nhiệm vụ (Phase 6 lát cắt 3, GAMEPLAY.md §9.3).
// Nội dung quest (điều kiện hoàn thành, thưởng) là SCRIPT thật qua ScriptSandbox (Điều 27) — Java
// chỉ lo cơ chế (lưu tiến độ, gọi đúng hook lúc đúng sự kiện, trao thưởng thật qua Port riêng).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
dependencies {
    implementation(project(":engine:engine-scripting"))
    implementation(libs.slf4j.api)
}
