// server-world — ECS simulation phía server: movement, combat, AI (SERVER.md).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-ecs"))
    api(project(":engine:engine-physics"))
    api(project(":shared:shared-protocol"))
    // Log tick quá hạn (SERVER.md §3) — server-gateway cấp binding Logback thật lúc runtime
    // (module thư viện chỉ cần API, không kéo theo binding — LOGGING/OBSERVABILITY §5).
    implementation("org.slf4j:slf4j-api:2.0.18")
}
