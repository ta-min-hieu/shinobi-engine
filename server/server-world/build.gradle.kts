// server-world — ECS simulation phía server: movement, combat, AI (SERVER.md).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-ecs"))
    api(project(":engine:engine-physics"))
    api(project(":shared:shared-protocol"))
    // ScriptSandbox (GraalJS, ADR-016) — nội dung skill (SkillSystem), Điều 27.
    implementation(project(":engine:engine-scripting"))
    // Log tick quá hạn (SERVER.md §3) — server-gateway cấp binding Logback thật lúc runtime
    // (module thư viện chỉ cần API, không kéo theo binding — LOGGING/OBSERVABILITY §5).
    implementation("org.slf4j:slf4j-api:2.0.18")
}

tasks.test {
    // Bộ kiểm thuộc tính mô phỏng thế giới (WorldSimulationPropertyTest) — chuyển tiếp cờ chạy tay
    // từ dòng lệnh Gradle vào JVM test: `-Dkage.worldFuzz.seeds=60 -Dkage.worldFuzz.ticks=36000`,
    // `-Dkage.worldFuzz.seed=<seed in ra lúc đỏ>`. Không đặt thì test dùng ngân sách mặc định của
    // cổng `check`. Vòng lớn vượt @Timeout — tắt bằng `-Djunit.jupiter.execution.timeout.mode=disabled`.
    for (key in listOf(
        "kage.worldFuzz.seed",
        "kage.worldFuzz.seeds",
        "kage.worldFuzz.ticks",
        "junit.jupiter.execution.timeout.mode",
    )) {
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}
