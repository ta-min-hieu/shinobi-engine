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
    implementation(project(":server:server-world"))
    implementation(project(":server:server-database"))
    implementation("io.netty:netty-all:4.1.136.Final")
    implementation("org.slf4j:slf4j-api:2.0.18")
    // Binding thật (Logback) — không có binding = log biến mất im lặng, không chấp nhận được
    // cho server (LOGGING/OBSERVABILITY §5 — JSON structured log ở production, text ở dev).
    runtimeOnly("ch.qos.logback:logback-classic:1.5.31")
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
