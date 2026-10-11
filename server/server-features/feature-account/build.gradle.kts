// feature-account — đăng nhập/tạo account, phát hành session (ADR-017).
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, KHÔNG dependency nào khác
// trong repo. Implementation thật (PostgresAccountAdapter, RedisSessionAdapter) nằm ở
// server-database (xem javadoc domain/Transaction.java — cùng quy ước với
// docs/templates/feature-module/domain/Transaction.java).
plugins {
    id("kage.quality-conventions")
}

dependencies {
    // Log cảnh báo khi kho session hỏng (2026-10-11) — slf4j-api, cùng thư viện các feature khác đã dùng.
    implementation(libs.slf4j.api)
}
