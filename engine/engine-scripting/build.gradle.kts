// engine-scripting — sandbox chạy script (GraalJS — ADR-016). Chỉ server (Điều 15).
plugins {
    id("kage.quality-conventions")
    alias(libs.plugins.jmh)
}
jmh {
    jmhVersion.set(libs.versions.jmh)
    // Cùng mẫu BENCHMARK.md §3 với engine-ecs. README dòng 70(c): đo trước khi đổi cách dựng Context.
    fork.set(2)
    warmupIterations.set(5)
    warmup.set("1s")
    iterations.set(10)
    timeOnIteration.set("1s")
    resultsFile.set(rootProject.file("benchmarks/results/engine-scripting-jmh-result.json"))
    resultFormat.set("JSON")
    // -PjmhIncludes=ProductionScriptBenchmark chạy riêng một benchmark (README dòng 70(b), 2026-10-23).
    providers.gradleProperty("jmhIncludes").orNull?.let { includes.set(listOf(it)) }
}
// Truffle (GraalJS) đòi `Multi-Release: true` trong manifest; uber jar của JMH làm mất nó và Truffle từ
// chối khởi động ("Multi-Release classes are not configured correctly").
tasks.named<Jar>("jmhJar") {
    manifest {
        attributes("Multi-Release" to "true")
    }
}
dependencies {
    api(project(":engine:engine-ecs"))
    // api, không implementation: ScriptSandbox.eval() trả về Value (org.graalvm.polyglot) — public
    // API của module này, module gọi vào (server-world) cần Value ở COMPILE classpath.
    api(libs.graaljs.polyglot)
    runtimeOnly(libs.graaljs.js)
    // js-isolate-community CHƯA thêm — xem javadoc ScriptSandbox: đóng gói lỗi thật (thiếu
    // darwin-amd64, không variant-aware theo OS), cần quyết định nền tảng server mục tiêu trước.
}
