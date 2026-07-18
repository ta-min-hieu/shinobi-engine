// client-desktop — CHỈ launcher LWJGL3 + DesktopPlatformAdapter (Điều 28).
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(project(":client:client-core"))
    // implementation(libs.gdx.backend.lwjgl3) — thêm ở Phase 1.
}
application {
    // Class chưa tồn tại — tạo ở Phase 1. `build` không cần class này, chỉ `run` cần.
    mainClass.set("dev.kage.client.desktop.DesktopLauncher")
}
