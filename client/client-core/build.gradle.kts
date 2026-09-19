// client-core — gameplay client + UI, platform-agnostic (CLIENT.md). Client chỉ hiển thị (Điều 15).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-core"))
    api(project(":engine:engine-ecs"))
    api(project(":engine:engine-physics"))
    api(project(":shared:shared-protocol"))
    // Chỉ API — binding thật (nếu cần) do launcher (client-desktop) quyết định, không phải
    // gameplay code (giữ client-core platform-agnostic, đúng tinh thần CLIENT.md).
    implementation("org.slf4j:slf4j-api:2.0.18")
    // FreeType sinh font runtime từ .ttf (RESOURCE.md §7) — đã duyệt sẵn trong TECHNOLOGY-STACK.md,
    // chỉ mới kích hoạt thật ở đây (không phải thư viện mới theo CLAUDE.md §4). gdx-freetype thuần
    // Java, không cần native platform jar ở module này — native chỉ cần ở nơi thật sự chạy app
    // (client-desktop), theo đúng cách gdx-platform natives-desktop đã làm.
    api(libs.gdx.freetype)
    // ADR-031 — nạp model glTF cho nhân vật/quái/NPC. Đến từ third-party/m2/ trong
    // repo, không phải jitpack.io (ADR-032). `api` vì kiểu của gdx-gltf sẽ xuất hiện
    // trong API render mà client-desktop nhìn thấy, giống gdx-freetype ngay trên.
    api(libs.gdx.gltf)
    // Test cần native FreeType thật để generateFont()/hasGlyph() chạy (FontFactoryVietnameseGlyphTest)
    // — test JVM chạy trực tiếp trên desktop, không qua client-desktop, nên cần natives ở đây.
    testImplementation(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
    // CharacterModelFrameBenchmarkTool (RENDERING.md §6.5 việc #3 / ADR-031 Revisit Trigger #1) cần
    // mở một Lwjgl3Application thật để có GL context đo GPU frame time — testImplementation CHỈ
    // (không main) vì đây là công cụ chẩn đoán thủ công, client-desktop production KHÔNG phụ thuộc
    // backend LWJGL3 qua client-core (Điều 28). Cả hai đã có sẵn trong TECHNOLOGY-STACK.md (dùng ở
    // client-desktop), không phải thư viện mới theo CLAUDE.md §4 — chỉ mở rộng phạm vi sang test
    // scope, cùng lý do gdx-freetype-platform natives-desktop ngay trên.
    testImplementation(libs.gdx.backend.lwjgl3)
    testImplementation(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    // client-core chỉ có slf4j-api (không binding — xem comment ở implementation slf4j-api trên) nên
    // LOG.info() của CharacterModelFrameBenchmarkTool sẽ rơi vào NOP logger và KHÔNG in ra console gì
    // cả nếu thiếu dòng này. logback-classic đã là dependency duyệt sẵn (server-gateway/server-database
    // dùng ở main scope) — đây chỉ thêm binding cho test scope để công cụ chẩn đoán có console output.
    testImplementation(libs.logback.classic)
}

// Font tiếng Việt lên CLASSPATH, lấy thẳng từ `assets/raw/fonts/` — MỘT nguồn sự thật, không nhân
// bản file .ttf thứ hai trong repo (RESOURCE.md §7 giữ nguyên vị trí gốc, và
// `FontFactoryVietnameseGlyphTest` vẫn đọc theo đường repo nên không bị ảnh hưởng).
//
// Vì sao classpath chứ không phải thư mục: `Gdx.files.internal` phân giải theo WORKING DIRECTORY
// rồi mới rơi về classpath. Bản cài khởi động từ chỗ người dùng đứng, không ai đảm bảo cwd là gốc
// asset — nhưng classpath thì luôn đúng. Đây chính là cách `i18n/strings` và `tls/dev-cert.pem`
// đang chạy, nên không phải cơ chế mới.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.dir("assets/raw/fonts")) {
        include("BeVietnamPro-Regular.ttf")
        into("fonts")
    }
}

// Chạy CharacterModelFrameBenchmarkTool — KHÔNG qua `test` (JUnit): cần MỞ CỬA SỔ GL thật, việc
// `test` không hỗ trợ và CI (ci.yml, ubuntu-latest, không Xvfb — xem FontFactoryVietnameseGlyphTest)
// sẽ luôn fail nếu chạy ở đó. Asset root giống hệt client-desktop/build.gradle.kts: workingDir phải
// là assets/exported/ vì Gdx.files.internal phân giải theo working directory.
tasks.register<JavaExec>("runFrameBenchmark") {
    group = "verification"
    description = "Đo GPU frame time với 20 model nhân vật cùng lúc (thủ công, cần GL context thật)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.kage.client.core.world.CharacterModelFrameBenchmarkTool")
    val assetRoot = rootProject.layout.projectDirectory.dir("assets/exported")
    workingDir = assetRoot.asFile
    // JFR có sẵn trong JDK (không phải thư viện mới theo CLAUDE.md §4) — tín hiệu cấp phát CHÍNH
    // XÁC hơn hẳn Runtime#freeMemory() thô ở CharacterModelFrameBenchmarkTool, dùng để trả lời câu
    // hỏi "SceneManager/AnimationController cấp phát bao nhiêu mỗi khung" ở RENDERING.md §12.2/§16
    // mà không cần thêm dependency profiler nào.
    jvmArgs(
        "-XX:StartFlightRecording=filename=${layout.buildDirectory.file("frame-benchmark.jfr").get().asFile.absolutePath},settings=profile,dumponexit=true")
    doFirst {
        // Sinh ra từ pipeline art, bị .gitignore (ART-PIPELINE §7) — mkdirs() để task không gãy ở
        // khâu Gradle khi checkout sạch chưa chạy pipeline (client tự rơi về hình thay thế).
        workingDir.mkdirs()
    }
}

// THROWAWAY (2026-08-09) — chẩn đoán báo cáo "nhân vật bị lộn ngược" sau khi wire clip jump
// (ADR-033). Xoá cùng CharacterOrientationDebugTool.java sau khi dùng xong, cùng tinh thần
// runFrameBenchmark phía trên (ảnh chụp thật, không suy đoán — RENDERING.md §11.1).
// THROWAWAY (2026-08-09) — xem trước bố cục HUD bằng ảnh, không cần chạy server. Xoá cùng
// HudPreviewTool.java khi giao diện đã chốt.
tasks.register<JavaExec>("runHudPreview") {
    group = "verification"
    description = "Chụp ảnh bố cục CombatHud với dữ liệu giả (THROWAWAY)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.kage.client.core.ui.hud.HudPreviewTool")
    val assetRoot = rootProject.layout.projectDirectory.dir("assets/exported")
    workingDir = assetRoot.asFile
    doFirst { workingDir.mkdirs() }
}

// Hợp đồng giữa hai module mà KHÔNG compiler nào canh: server gửi `ErrorResponse.error_key` để
// client dùng THẲNG làm khoá i18n (xem javadoc FactionController.errorMessageKey). Rà bằng máy
// 2026-09-19: server sinh 25 khoá `error.*` mà bundle chỉ có 10 — tức bất biến ấy đã SAI trong im
// lặng một thời gian dài, và hậu quả là người chơi hoặc không thấy gì, hoặc thấy `??error.abc??`.
//
// Đặt ở client-core chứ không ở root: bundle là tài sản của module NÀY, nên nó là chỗ hợp lý để
// chịu trách nhiệm phủ hết. Chỉ ĐỌC file nguồn của server lúc build — không thêm dependency module,
// không đụng classpath. Cùng khuôn với các task ở dưới đã đọc `rootProject` cho `assets/exported`.
val verifyServerErrorKeys = tasks.register("verifyServerErrorKeys") {
    group = "verification"
    description = "Mọi error_key server gửi phải có bản dịch trong CẢ HAI bundle i18n."
    val serverSources = rootProject.fileTree("server") { include("**/src/main/java/**/*.java") }
    val bundles = listOf("vi", "en").map {
        layout.projectDirectory.file("src/main/resources/i18n/strings_$it.properties").asFile
    }
    val stamp = layout.buildDirectory.file("verifyServerErrorKeys.txt")
    inputs.files(serverSources).withPropertyName("serverSources")
    inputs.files(bundles).withPropertyName("i18nBundles")
    outputs.file(stamp)
    doLast {
        val literal = Regex("\"(error\\.[a-z0-9_]+)\"")
        val produced = sortedSetOf<String>()
        serverSources.forEach { file ->
            literal.findAll(file.readText()).forEach { produced.add(it.groupValues[1]) }
        }
        val missing = mutableListOf<String>()
        bundles.forEach { bundle ->
            val translated = bundle.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .map { it.substringBefore("=").trim() }
                .toSet()
            produced.minus(translated).forEach { missing.add("${bundle.name}: thiếu $it") }
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "error_key server gửi mà bundle không dịch được — người chơi sẽ thấy câu chung " +
                    "thay vì lý do thật:\n  " + missing.joinToString("\n  ")
            )
        }
        stamp.get().asFile.writeText("${produced.size} khoá error.* của server, đủ bản dịch ở cả hai bundle\n")
    }
}

tasks.named("check") { dependsOn(verifyServerErrorKeys) }

// ĐO (không chụp) chiều cao mọi cửa sổ HUD có danh sách ở trạng thái TỆ NHẤT, fail nếu cao hơn
// 720p. KHÔNG throwaway: mỗi lần thêm dòng/nút vào một cửa sổ là một lần cần chạy lại — build gate
// không bắt được vì cần GL context thật (CI không có display).
tasks.register<JavaExec>("runHudLayoutCheck") {
    group = "verification"
    description = "Đo chiều cao các cửa sổ HUD khi danh sách đầy; fail nếu vượt màn hình 720p."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.kage.client.core.ui.hud.HudLayoutBudgetTool")
    val assetRoot = rootProject.layout.projectDirectory.dir("assets/exported")
    workingDir = assetRoot.asFile
    providers.gradleProperty("kageLayoutRows").orNull?.let { systemProperty("kage.layout.rows", it) }
    doFirst { workingDir.mkdirs() }
}

// Xem trước thế giới bằng ảnh, không cần server (~5s). KHÔNG throwaway: mỗi lần thêm/sửa một model
// quái hay NPC là một lần cần nhìn lại hướng mặt, kích thước và dấu mục tiêu — thứ build gate không
// bắt được.
tasks.register<JavaExec>("runWorldPreview") {
    group = "verification"
    description = "Chụp ảnh nhân vật/quái/NPC (model 3D, hộp thay thế, dấu mục tiêu) với dữ liệu giả."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.kage.client.core.world.WorldModelPreviewTool")
    // cwd = gốc asset: Gdx.files.internal("models/med/...") tính từ đây (bài học asset-root-cwd).
    val assetRoot = rootProject.layout.projectDirectory.dir("assets/exported")
    workingDir = assetRoot.asFile
    // Ảnh ra ngoài cây asset — assets/exported là tài sản pipeline art, không phải chỗ đổ output.
    systemProperty("kage.preview.out", layout.buildDirectory.dir("preview-world").get().asFile.absolutePath)
    doFirst { workingDir.mkdirs() }
}

tasks.register<JavaExec>("runOrientationDebug") {
    group = "verification"
    description = "Chụp clip idle + jump của model để chẩn đoán bug lộn ngược (THROWAWAY)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.kage.client.core.world.CharacterOrientationDebugTool")
    val assetRoot = rootProject.layout.projectDirectory.dir("assets/exported")
    workingDir = assetRoot.asFile
    doFirst { workingDir.mkdirs() }
}
