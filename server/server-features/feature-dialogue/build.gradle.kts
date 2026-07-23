// feature-dialogue — hội thoại NPC (Phase 6 lát cắt 2, GAMEPLAY.md §9.2). Nội dung dialogue là
// SCRIPT thật qua ScriptSandbox (Điều 27, SCRIPTING.md §2 — "Dialogue NPC" nằm thẳng trong cột
// Script, không có lựa chọn config/hardcode nào khác).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
// engine-scripting là dependency HỢP LỆ ở application/ (thư viện engine dùng chung, không phải hạ
// tầng DB/network — khác lý do domain/ vẫn không được đụng tới nó, chỉ application/ mới eval script).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    implementation(project(":engine:engine-scripting"))
    implementation(libs.slf4j.api)
}
