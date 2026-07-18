// server-database — Adapter PostgreSQL/Redis. MODULE DUY NHẤT được import JDBC/Redis (Điều 23).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":shared:shared-common"))
    // implementation(libs.hikaricp), implementation(libs.postgresql) — thêm ở Phase 3.
}
