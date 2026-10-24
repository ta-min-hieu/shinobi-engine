// server-database — Adapter PostgreSQL/Redis. MODULE DUY NHẤT được import JDBC/Redis (Điều 23,
// enforce bằng ArchUnit chi_server_database_dung_jdbc/chi_server_database_dung_redis).
//
// Phụ thuộc NGƯỢC vào feature-account (chỉ để thấy interface Port trong domain/) — đúng
// Dependency Inversion: module cấp thấp (hạ tầng) implement abstraction do module cấp cao
// (nghiệp vụ) định nghĩa, không phải ngược lại. Xem feature-account/CLAUDE.md mục "Khác template
// chuẩn" để biết lý do PostgresAccountAdapter/RedisSessionAdapter nằm ở ĐÂY chứ không nằm trong
// feature-account/infrastructure/ như docs/templates/feature-module minh hoạ.
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":shared:shared-common"))
    implementation(project(":server:server-features:feature-account"))
    implementation(project(":server:server-features:feature-character"))
    implementation(project(":server:server-features:feature-inventory"))
    implementation(project(":server:server-features:feature-shop"))
    implementation(project(":server:server-features:feature-dialogue"))
    implementation(project(":server:server-features:feature-quest"))
    implementation(project(":server:server-features:feature-friend"))
    implementation(project(":server:server-features:feature-mail"))
    implementation(project(":server:server-features:feature-chat"))
    implementation(project(":server:server-features:feature-guild"))
    implementation(project(":server:server-features:feature-party"))
    implementation(project(":server:server-features:feature-trade"))
    implementation(project(":server:server-features:feature-pvp"))
    implementation(project(":server:server-features:feature-monster"))
    implementation(project(":server:server-features:feature-world"))
    implementation(project(":server:server-features:feature-achievement"))
    implementation(project(":server:server-features:feature-checkin"))
    implementation(project(":server:server-features:feature-crafting"))
    implementation(project(":server:server-features:feature-market"))
    implementation(project(":server:server-features:feature-gm"))

    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.jedis)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.test.integration)
    // Không có binding SLF4J thật → Testcontainers nuốt mất log chẩn đoán "vì sao mỗi
    // strategy Docker fail" (chỉ còn 1 dòng IllegalStateException cụt lủn). Cần logback thật
    // ở test để debug được, không chỉ ở runtime thật (server-gateway đã làm y hệt).
    testRuntimeOnly(libs.logback.classic)
}

// Bài kiểm bảo toàn kinh tế (EconomyConservationStressIntegrationTest) đọc seed/thời lượng/số luồng
// từ system property `kage.econ.*`. Test chạy ở JVM con nên -D trên dòng lệnh Gradle không tự tới
// được — chuyển tiếp đúng nhóm khoá này. Không đặt gì thì test dùng mặc định vừa ngân sách `check`.
tasks.withType<Test>().configureEach {
    systemProperties(providers.systemPropertiesPrefixedBy("kage.econ.").get())
    // Cùng lý do cho bộ fuzz máy trạng thái quest (QuestStateMachinePropertyIntegrationTest).
    systemProperties(providers.systemPropertiesPrefixedBy("kage.questFuzz.").get())
}
