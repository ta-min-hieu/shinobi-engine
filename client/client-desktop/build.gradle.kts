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
    // Native FreeType — client-core khai báo gdx-freetype (API thuần Java), launcher thật quyết
    // định native platform nào cần (cùng lý do gdx-platform natives-desktop ở trên).
    implementation(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}
application {
    mainClass.set("dev.kage.client.desktop.DesktopLauncher")
}
