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
    // Backend log CHO BẢN CHẠY THẬT. Không có nó, SLF4J rơi về NOP và MỌI dòng log của client biến
    // mất — kể cả "Chưa có model '<khoá>' — thực thể này vẽ bằng hộp" của WorldModelRenderer, đúng
    // câu cần đọc nhất khi một con quái ra hình hộp đỏ. Phát hiện thật trong buổi playtest
    // 2026-08-30: chẩn đoán phải đoán mò vì client im lặng tuyệt đối ("No SLF4J providers were
    // found"). runtimeOnly: không dòng code nào import logback, chỉ cần nó có mặt lúc chạy.
    runtimeOnly(libs.logback.classic)
}
application {
    mainClass.set("dev.kage.client.desktop.DesktopLauncher")
}

// Asset root lúc chạy dev. `Gdx.files.internal` trên backend LWJGL3 phân giải TƯƠNG ĐỐI so với
// working directory, mà working directory mặc định của task `run` là thư mục SUBPROJECT
// (client/client-desktop/), không phải gốc repo — nên nếu không đặt ở đây thì mọi model/atlas đều
// "không tồn tại" và CharacterModelRenderer sẽ lặng lẽ tắt nhánh 3D (ADR-031) rồi vẽ hình hộp.
// `assets/exported/` là runtime asset root theo RESOURCE.md §2.
tasks.named<JavaExec>("run") {
    val assetRoot = rootProject.layout.projectDirectory.dir("assets/exported")
    workingDir = assetRoot.asFile
    doFirst {
        // `assets/exported/` là sản phẩm sinh ra và bị .gitignore (ART-PIPELINE §7), nên checkout
        // sạch chưa chạy pipeline art sẽ KHÔNG có thư mục này. Tạo rỗng để `run` vẫn chạy được —
        // client tự rơi về hình thay thế, đúng hành vi đã thiết kế, thay vì gãy ở khâu Gradle.
        workingDir.mkdirs()
    }
}
