// server-gateway — Netty entrypoint. MODULE DUY NHẤT được import io.netty.* (Điều 23, ADR-003).
//
// Phụ thuộc server-database CHỈ để gọi ServerDatabaseBootstrap.wireAccountFacade() trong
// GatewayBootstrap.main() (composition root) — không bao giờ import java.sql/Jedis trực tiếp,
// ArchUnit vẫn enforce đúng theo package (dev.kage.server.database), không theo Gradle module.
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(project(":shared:shared-protocol"))
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
    implementation(project(":server:server-world"))
    implementation(project(":server:server-database"))
    implementation(project(":engine:engine-scripting"))
    implementation("io.netty:netty-all:4.2.17.Final")
    implementation("org.slf4j:slf4j-api:2.0.18")
    // Metrics (ADR-015) — MODULE DUY NHẤT được import io.micrometer.* (server-world dùng qua
    // TickMetricsPort, cùng lý do chỉ gateway biết Netty).
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.registry.prometheus)
    // Tracing (ADR-015, Phase 9 lát cắt 9 quan sát lớn) — MODULE DUY NHẤT import io.opentelemetry.*,
    // cùng lý do server-gateway là module duy nhất import io.micrometer.*.
    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    // Binding thật (Logback) — không có binding = log biến mất im lặng, không chấp nhận được
    // cho server (LOGGING/OBSERVABILITY §5 — JSON structured log ở production, text ở dev).
    // `implementation` (không phải `runtimeOnly`) — JsonLogEncoder (Phase 9 lát cắt 9) tự viết,
    // KHÔNG dùng logstash-logback-encoder (thư viện ngoài không có trong TECHNOLOGY-STACK.md), nên
    // cần API Logback lúc COMPILE, không chỉ lúc chạy.
    implementation("ch.qos.logback:logback-classic:1.5.31")
}
application {
    mainClass.set("dev.kage.server.gateway.GatewayBootstrap")
    // Leak detector paranoid ở dev (JVM.md §8, ROADMAP Phase 2) — bắt sớm chỗ quên
    // ByteBuf.release(). Production dùng "simple" (chi phí thấp hơn) — set ở lúc deploy thật,
    // không cứng ở đây vì đây là default cho `./gradlew run` (dev), không phải jar production.
    applicationDefaultJvmArgs = listOf("-Dio.netty.leakDetection.level=paranoid")
}

tasks.test {
    // Test cũng chạy qua pipeline Netty thật (EmbeddedChannel + socket thật) — bật paranoid ở
    // đây để CI tự bắt leak ByteBuf (quên release()) thay vì đợi phát hiện lúc chạy server thật.
    systemProperty("io.netty.leakDetection.level", "paranoid")
}
