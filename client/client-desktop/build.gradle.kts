// client-desktop — CHỈ launcher LWJGL3 + DesktopPlatformAdapter (Điều 28).
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(project(":client:client-core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.14.2")
    // Native binary bắt buộc cho LWJGL3 chạy — TOML không hỗ trợ classifier trực tiếp nên áp
    // dụng bằng variantOf (gradle/libs.versions.toml giải thích lý do).
    implementation(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}
application {
    mainClass.set("dev.kage.client.desktop.DesktopLauncher")
}
