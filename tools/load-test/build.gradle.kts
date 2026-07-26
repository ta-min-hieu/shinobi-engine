// tools:load-test — client giả lập nhiều người chơi qua SOCKET TCP THẬT (BENCHMARK.md §5, Phase 9
// lát cắt 2). KHÔNG chạy trong game (REPOSITORY-STRUCTURE.md §2 "tools/* — CLI build-time"),
// KHÔNG phụ thuộc server-gateway/server-world (chỉ nói chuyện qua chính giao thức dây — đúng cách
// người chơi thật kết nối, không gọi tắt qua API nội bộ).
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(project(":shared:shared-protocol"))
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}
application {
    mainClass.set("dev.kage.tools.loadtest.LoadTestRunner")
}
