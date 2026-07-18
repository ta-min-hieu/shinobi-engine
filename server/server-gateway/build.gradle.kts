// server-gateway — Netty entrypoint. MODULE DUY NHẤT được import io.netty.* (Điều 23, ADR-003).
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(project(":shared:shared-protocol"))
    // implementation(libs.netty.all) — thêm ở Phase 2.
}
application {
    mainClass.set("dev.kage.server.gateway.GatewayBootstrap")   // tạo ở Phase 2
}
