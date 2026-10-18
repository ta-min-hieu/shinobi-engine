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
    // JDK 24+ cảnh báo "restricted method System::load" (gdx-jnigen-loader nạp native LWJGL/
    // FreeType) và báo trước sẽ CHẶN hẳn ở JDK sau. Cho phép tường minh để bản cài không gãy khi
    // người chơi chạy JDK mới hơn (ROADMAP v1 — mục rủi ro LWJGL, kiểm nguồn 2026-10-01).
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
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

// ---------------------------------------------------------------------------------------------
// ADR-053 (Accepted 2026-10-18) — GÓI WINDOWS MANG THEO RUNTIME JAVA 25: jlink + `jpackage --type
// app-image` + zip, task Exec trực tiếp, KHÔNG plugin. Ghim JDK của người chơi: rủi ro JNI của LWJGL
// trên JDK 28+ và việc JDK sau cấm `System::load` không còn chạm tới bản phát hành.
//
// Dựng (trên Windows, cần một JDK 25 ĐẦY ĐỦ — JBR của IntelliJ không có jlink/jpackage):
//   ./gradlew :client:client-desktop:packageWindowsClient -PkageBundleAssets -PkagePackagingJdk=<JDK 25>
// Ra: build/distributions/kage-client-<kageClientVersion>-windows-x64.zip
//
// Tách khỏi build/check (D5): chỉ chạy khi gọi tên. `installDist` + `.bat` giữ nguyên cho dev.
// ---------------------------------------------------------------------------------------------

// D2 — module KHAI TƯỜNG MINH. jdeps chỉ là cổng kiểm: nó KHÔNG thấy `jdk.localedata` (đo 2026-10-01:
// thiếu module này thì số tiếng Việt thành `1,234,567.5`), nên không lấy thẳng output của nó.
val clientRuntimeModules = listOf("java.base", "java.desktop", "java.logging", "jdk.unsupported", "jdk.localedata")
val clientLocales = "en,vi" // theo ClientLanguage; thêm ngôn ngữ thứ ba thì sửa ở đây
val packagingJdkPath = providers.gradleProperty("kagePackagingJdk")
// D7 — repo không khai `version` (đổi nó là đổi tên mọi jar), nên phiên bản gói đi riêng.
val clientVersion = providers.gradleProperty("kageClientVersion").orElse("0.10.0")
val windowsExe = if (System.getProperty("os.name").lowercase().contains("win")) ".exe" else ""

/** D4 — JDK đóng gói được CHỈ ĐỊNH, không đoán; thiếu gì thì nói rõ cần gì. */
fun packagingJdk(): File {
    val path = packagingJdkPath.orNull
        ?: throw GradleException(
            "Thiếu -PkagePackagingJdk=<đường dẫn JDK 25 đầy đủ> (ADR-053 D4). JBR của IntelliJ không có jlink/jpackage.")
    val jdk = file(path)
    for (tool in listOf("jlink", "jpackage", "jdeps")) {
        if (!jdk.resolve("bin/$tool$windowsExe").isFile) {
            throw GradleException("JDK đóng gói $path không có bin/$tool$windowsExe (ADR-053 D4).")
        }
    }
    val release = jdk.resolve("release")
    if (!release.isFile || !release.readText().contains("JAVA_VERSION=\"25")) {
        throw GradleException("JDK đóng gói $path không phải Java 25 (đọc file release) — ADR-053 D4.")
    }
    return jdk
}

val packagingInput = layout.buildDirectory.dir("jpackage-input")
val clientRuntimeDir = layout.buildDirectory.dir("jlink/runtime")
val appImageDir = layout.buildDirectory.dir("jpackage")

// Q7 — chỉ Windows x64 (D8): bỏ jar native LWJGL của nền tảng khác (~7 MB). gdx-platform/freetype
// gói mọi nền tảng trong MỘT jar nên giữ nguyên.
val prepareClientPackagingInput = tasks.register<Sync>("prepareClientPackagingInput") {
    dependsOn("installDist")
    from(layout.buildDirectory.dir("install/client-desktop/lib"))
    exclude("*natives-linux*", "*natives-macos*", "*natives-windows-x86*", "*natives-windows-arm64*", "*natives-freebsd*")
    into(packagingInput)
}

// D2 — cổng: jdeps báo module nào KHÔNG có trong danh sách ⇒ đỏ (dependency mới kéo module mới).
val checkClientRuntimeModules = tasks.register("checkClientRuntimeModules") {
    dependsOn(prepareClientPackagingInput)
    doLast {
        val jdeps = packagingJdk().resolve("bin/jdeps$windowsExe")
        val inputDir = packagingInput.get().asFile
        val process = ProcessBuilder(
                jdeps.path, "--ignore-missing-deps", "--multi-release", "25", "--recursive",
                "--print-module-deps", "--class-path", inputDir.resolve("*").path,
                inputDir.resolve("client-desktop.jar").path)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            throw GradleException("jdeps lỗi (exit ${process.exitValue()}): $text")
        }
        val found = text.trim().lines().last().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val missing = found.filterNot { it in clientRuntimeModules }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "jdeps thấy module không có trong clientRuntimeModules: $missing — thêm vào danh sách (ADR-053 D2).")
        }
    }
}

val clientRuntime = tasks.register<Exec>("clientRuntime") {
    dependsOn(checkClientRuntimeModules)
    doFirst {
        delete(clientRuntimeDir) // jlink từ chối thư mục đã tồn tại
        executable = packagingJdk().resolve("bin/jlink$windowsExe").path
        args(
            "--add-modules", clientRuntimeModules.joinToString(","),
            "--include-locales=$clientLocales",
            "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=zip-9",
            "--output", clientRuntimeDir.get().asFile.path)
    }
}

val clientAppImage = tasks.register<Exec>("clientAppImage") {
    dependsOn(clientRuntime)
    doFirst {
        delete(appImageDir)
        executable = packagingJdk().resolve("bin/jpackage$windowsExe").path
        args(
            "--type", "app-image",
            "--name", "KageClient",
            "--app-version", clientVersion.get(),
            "--input", packagingInput.get().asFile.path,
            "--main-jar", "client-desktop.jar",
            "--main-class", "dev.kage.client.desktop.DesktopLauncher",
            // D3 — giữ đúng cờ của script hôm nay; KHÔNG ghim JNINativeInterfaceSize (runtime là 25).
            "--java-options", "--enable-native-access=ALL-UNNAMED",
            "--runtime-image", clientRuntimeDir.get().asFile.path,
            "--dest", appImageDir.get().asFile.path)
    }
}

tasks.register<Zip>("packageWindowsClient") {
    group = "distribution"
    description = "Gói Windows x64 mang theo runtime Java 25 (ADR-053): KageClient.exe + app/ + runtime/, zip."
    dependsOn(clientAppImage)
    archiveFileName.set(clientVersion.map { "kage-client-$it-windows-x64.zip" })
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(appImageDir)
}
