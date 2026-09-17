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

// ---------------------------------------------------------------------------------------------
// BẢN CÀI WINDOWS KHÔNG KHỞI ĐỘNG ĐƯỢC — và lỗi này có TRƯỚC mọi thay đổi đóng gói asset.
//
// Plugin `application` sinh script khởi động liệt kê TỪNG jar vào `set CLASSPATH=...`. Đo thật
// 2026-09-16: dòng đó dài 3.324 ký tự nguyên văn với 70 lần `%APP_HOME%`, và `%APP_HOME%` nở ra
// 81 ký tự ⇒ **8.294 ký tự**, trong khi `cmd.exe` chỉ nhận **8.191**. Kết quả:
//     The input line is too long.
// `cmd` chết TRƯỚC khi Java kịp chạy, nên không có lấy một dòng log để chẩn đoán.
//
// ⚠️ Bỏ hẳn `kage-assets.jar` ra thì còn **8.192** — VẪN vượt, đúng một ký tự. Tức bản cài Windows
// đã hỏng sẵn; jar asset chỉ làm nó tệ thêm chứ không gây ra.
//
// Cách sửa: Java giãn được ký tự đại diện trong classpath (`dir/*`), nên thay cả danh sách bằng
// một mục. Dòng còn ~30 ký tự và MIỄN NHIỄM với mọi jar thêm sau này — quan trọng, vì nguyên nhân
// gốc là số jar chỉ có tăng (riêng LWJGL đã mang native của bảy nền tảng).
// ---------------------------------------------------------------------------------------------
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            Regex("(?m)^set CLASSPATH=.*$").replace(windowsScript.readText()) {
                "set CLASSPATH=%APP_HOME%/lib/*"
            })
        unixScript.writeText(
            Regex("(?m)^CLASSPATH=.*$").replace(unixScript.readText()) {
                "CLASSPATH=" + "$" + "APP_HOME/lib/*"
            })
    }
}

// ---------------------------------------------------------------------------------------------
// BẢN CÀI CHO NGƯỜI CHƠI — asset đi cùng, không bắt họ có repo.
//
// Đo thật 2026-09-16: `installDist` ra `bin/` + `lib/` (69 jar) và KHÔNG một file `.glb`/`.atlas`
// nào. Tức bản cài chạy được nhưng mọi nhân vật/quái ra hình hộp, vì `assets/exported/` là sản
// phẩm sinh (ART-PIPELINE §7) và chưa bao giờ được đóng vào bản phát hành.
//
// Đóng thành JAR chứ không phải thư mục: `Gdx.files.internal` phân giải theo WORKING DIRECTORY rồi
// mới rơi về classpath. Bản cài khởi động từ chỗ người dùng đứng nên cwd không đảm bảo, còn
// classpath thì luôn đúng — và plugin `application` tự đưa mọi jar runtime vào `lib/` LẪN dòng
// CLASSPATH của script khởi động, nên không phải sửa tay script.
//
// Chỉ tier MEDIUM (ADR-028): đo thật models/med 24M + atlases 4,2M ≈ 28M. LOW/HIGH tải theo yêu
// cầu là `ensureAssetsAvailable(tier)` — CHƯA code ở đâu trong repo, ngoài phạm vi v1.
//
// ⚠️ GẮN SAU MỘT CỜ, mặc định TẮT. `assets/exported/` bị gitignore nên CI (ci.yml, ubuntu-latest,
// không Blender) KHÔNG có nó; khai `runtimeOnly` vô điều kiện sẽ làm `./gradlew build` đỏ trên CI.
// Dựng bản phát hành:  ./gradlew :client:client-desktop:installDist -PkageBundleAssets
// ---------------------------------------------------------------------------------------------
val assetsJar = tasks.register<Jar>("assetsJar") {
    group = "distribution"
    description = "Đóng asset tier MEDIUM thành jar để bản cài mang theo (ADR-028)."
    archiveBaseName.set("kage-assets")
    val exportedRoot = rootProject.layout.projectDirectory.dir("assets/exported")
    from(exportedRoot) {
        include("models/med/**")
        include("atlases/**")
    }
    doFirst {
        // Hỏng TO TIẾNG, không lặng lẽ ra một bản cài rỗng: một jar asset trống nhìn y hệt jar
        // đầy cho tới lúc người chơi mở game lên và thấy toàn hộp.
        val modelsDir = exportedRoot.dir("models/med").asFile
        if (!modelsDir.isDirectory || modelsDir.listFiles().isNullOrEmpty()) {
            throw GradleException(
                "Không có asset để đóng gói: " + modelsDir.absolutePath + " rỗng hoặc không tồn tại. "
                    + "Chạy dây chuyền art (ART-PIPELINE §7) trước khi dựng bản cài.")
        }
    }
}

if (providers.gradleProperty("kageBundleAssets").isPresent) {
    dependencies {
        runtimeOnly(files(assetsJar))
    }
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
