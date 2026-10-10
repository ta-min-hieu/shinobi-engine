// engine-ecs — bọc ECS framework. NƠI DUY NHẤT được import com.badlogic.ashley.* (ADR-006).
// KHÔNG phụ thuộc engine-core (server không được kéo theo rendering — ENGINE.md §9).
plugins {
    id("kage.quality-conventions")
    alias(libs.plugins.jmh)
}
jmh {
    jmhVersion.set(libs.versions.jmh)
    // BENCHMARK.md §3 mẫu chuẩn dự án — Fork 2 JVM riêng (tránh nhiễu), warmup 5x1s, đo 10x1s.
    fork.set(2)
    warmupIterations.set(5)
    warmup.set("1s")
    iterations.set(10)
    timeOnIteration.set("1s")
    resultsFile.set(rootProject.file("benchmarks/results/engine-ecs-jmh-result.json"))
    resultFormat.set("JSON")
}
tasks.test {
    // Các bộ kiểm thuộc tính của engine-ecs (EcsWorldModelPropertyTest và các bộ ở system/) — chuyển
    // tiếp cờ chạy tay từ dòng lệnh Gradle vào JVM test: `-Dkage.ecsFuzz.seeds=3000
    // -Dkage.ecsFuzz.steps=6000`, `-Dkage.ecsFuzz.seed=<seed in ra lúc đỏ>`. Không đặt thì test dùng
    // ngân sách mặc định của cổng `check`. Vòng lớn vượt @Timeout — tắt bằng
    // `-Djunit.jupiter.execution.timeout.mode=disabled`.
    for (key in listOf(
        "kage.ecsFuzz.seed",
        "kage.ecsFuzz.seeds",
        "kage.ecsFuzz.steps",
        "junit.jupiter.execution.timeout.mode",
    )) {
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}
dependencies {
    api(project(":shared:shared-common"))
    // ⚠️ "api" là BẮT BUỘC, không thể "implementation": dev.kage.engine.ecs.Component EXTENDS
    // ashley's Component (đó chính là cơ chế bọc — xem Component.java javadoc). javac biên dịch
    // bất kỳ class nào `implements dev.kage.engine.ecs.Component` ở module khác đều cần Ashley
    // resolvable trên classpath, dù code đó không viết `import com.badlogic.ashley...` bao giờ.
    //
    // Điều này KHÔNG vi phạm ADR-006/ArchUnit: ArchUnit kiểm dependency ở mức BYTECODE
    // (constant pool của chính class đó) — HealthComponent.class chỉ tham chiếu
    // dev.kage.engine.ecs.Component, không tham chiếu ashley.Component trực tiếp, dù cả hai đều
    // cần có mặt trên classpath để compile. "Compile classpath visibility" (Gradle api/
    // implementation) và "bytecode dependency" (ArchUnit) là hai khái niệm khác nhau.
    api("com.badlogicgames.ashley:ashley:1.7.4")
}
