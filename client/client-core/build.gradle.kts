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

// Hợp đồng THỨ HAI cùng loại, tìm ra bằng chính mẹo đã tìm ra cái trên: nội dung sống trong
// migration SQL của server, chữ hiện lên màn hình sống trong bundle của client, và KHÔNG có gì nối
// hai bên. Rà bằng máy 2026-09-19: 20 nhiệm vụ trong `quest_template` mà bundle chỉ dịch 15, và
// `sample_boss` — con boss DUY NHẤT của hầm ngục — không có tên. Người chơi thấy
// `??quest.name.foundation_breakthrough??` ngay trên HUD.
//
// Hai nguồn khoá, vì client lấy chữ bằng HAI đường khác nhau:
//   1. `name_key` server ghi thẳng vào bảng (`item.*`, `hud.monster.*`, `npc.*`) — client nhận qua
//      dây hoặc tự ghép, nhưng chuỗi khoá có mặt NGUYÊN VĂN trong file .sql.
//   2. `quest.name.<code>` — KHÔNG có trong .sql chút nào: client tự ghép từ `quest_code` trên dây
//      (CombatHud.setQuest, QuestCoordinator.questLine). Đây đúng là loại hợp đồng chỉ tồn tại
//      trong đầu người viết, nên nó là loại đứt đầu tiên.
//
// Đọc file .sql chứ KHÔNG nối tới Postgres: cổng build phải chạy được trên máy không có DB (cùng lý
// do `verifyServerErrorKeys` chỉ đọc file .java). Đã đối chiếu một lần với DB thật: 20/20 mã nhiệm
// vụ và 14/14 khoá quái trích ra từ .sql khớp từng dòng với bảng.
val verifyContentKeys = tasks.register("verifyContentKeys") {
    group = "verification"
    description = "Mọi khoá nội dung của server (SQL + script + quest.name.<code>) phải dịch được ở CẢ HAI bundle."
    val migrations = rootProject.fileTree("server/server-database/src/main/resources/db/migration") {
        include("*.sql")
    }
    // Nguồn thứ BA: script GraalJS của hội thoại/nhiệm vụ gửi thẳng `text_key`/`label_key` cho
    // client ({@code DialoguePanel#text}). Chỉ lấy `src/main/resources` — fixture trong
    // `src/test/resources` cố tình dùng khoá không có bản dịch và không bao giờ tới mắt người chơi.
    val scripts = rootProject.fileTree("server") {
        include("**/src/main/resources/scripts/**/*.js")
    }
    val bundles = listOf("vi", "en").map {
        layout.projectDirectory.file("src/main/resources/i18n/strings_$it.properties").asFile
    }
    val stamp = layout.buildDirectory.file("verifyContentKeys.txt")
    inputs.files(migrations).withPropertyName("migrations")
    inputs.files(scripts).withPropertyName("contentScripts")
    inputs.files(bundles).withPropertyName("i18nBundles")
    outputs.file(stamp)
    doLast {
        // Khoá i18n trong .sql luôn là literal nháy đơn dạng `a.b.c` chữ thường. Cố tình BẮT RỘNG:
        // một literal lọt vào đây mà không phải khoá i18n thì thà sửa regex này còn hơn để một tên
        // hiển thị trôi ra bản build mà không ai thấy.
        val keyLiteral = Regex("""'([a-z][a-z0-9_]*(?:\.[a-z0-9_]+){1,4})'""")
        // Mỗi INSERT vào `quest_template` đều kết bằng mệnh đề chống trùng này (khuôn V26→V48).
        val questCode = Regex("FROM quest_template WHERE code = '([a-z0-9_]+)'")
        val needed = sortedSetOf<String>()
        migrations.forEach { file ->
            val sql = file.readText()
            keyLiteral.findAll(sql).forEach { needed.add(it.groupValues[1]) }
            questCode.findAll(sql).forEach { needed.add("quest.name." + it.groupValues[1]) }
        }
        val scriptLiteral = Regex("""["]([a-z][a-z0-9_]*(?:\.[a-z0-9_]+){1,4})["]""")
        scripts.forEach { file ->
            scriptLiteral.findAll(file.readText()).forEach { needed.add(it.groupValues[1]) }
        }
        val missing = mutableListOf<String>()
        bundles.forEach { bundle ->
            val translated = bundle.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .map { it.substringBefore("=").trim() }
                .toSet()
            needed.minus(translated).forEach { missing.add("${bundle.name}: thiếu $it") }
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Nội dung server có mà bundle không dịch được — người chơi sẽ thấy `??khoá??` " +
                    "thay vì tên:\n  " + missing.joinToString("\n  ")
            )
        }
        stamp.get().asFile.writeText("${needed.size} khoá nội dung của server, đủ bản dịch ở cả hai bundle\n")
    }
}

tasks.named("check") { dependsOn(verifyContentKeys) }

// Hợp đồng THỨ BA cùng loại: `CombatConstants` phía client tự ghi trong javadoc của chính nó
// "PHẢI khớp server-world.CombatConstants/SkillConstants/SkillRegistry" rồi CHÉP TAY 4 hằng số +
// 5 skill hệ phái (code/cooldown/chi slot 0) từ ba file phía server, chỉ nối bằng đúng một dòng
// comment — không có gì bắt drift khi cân bằng đổi số (mọi giá trị ở cả 4 file đang ghi rõ
// "PLACEHOLDER, chưa cân bằng qua playtest"). Đọc .java làm text, KHÔNG phụ thuộc compile-time vào
// server-world (kiến trúc cấm client-core biết server, giống lý do verifyServerErrorKeys chỉ đọc
// text). SkillRegistry.ALL dựng bằng `new SkillDefinition(...)` nhiều dòng — riêng slot 0 hệ Hỏa
// TRUYỀN THẲNG hằng số SkillConstants.FIREBALL_* (không lặp literal) trong khi 4 hệ còn lại dùng
// literal trực tiếp; regex bắt RAW TOKEN ở từng vị trí tham số rồi giải nghĩa (literal hoặc tra
// bảng hằng số FIREBALL_* đã trích ở trên) để xử lý đều cả hai kiểu mà không cần rẽ nhánh Hỏa riêng.
val verifyCombatConstantsMatchServer = tasks.register("verifyCombatConstantsMatchServer") {
    group = "verification"
    description = "CombatConstants phía client phải khớp CombatConstants/SkillConstants/SkillRegistry phía server."
    val serverCombatConstants = rootProject.layout.projectDirectory.file(
        "server/server-world/src/main/java/dev/kage/server/world/CombatConstants.java")
    val serverSkillConstants = rootProject.layout.projectDirectory.file(
        "server/server-world/src/main/java/dev/kage/server/world/SkillConstants.java")
    val serverSkillRegistry = rootProject.layout.projectDirectory.file(
        "server/server-world/src/main/java/dev/kage/server/world/SkillRegistry.java")
    val clientCombatConstants =
        layout.projectDirectory.file("src/main/java/dev/kage/client/core/world/CombatConstants.java")
    val stamp = layout.buildDirectory.file("verifyCombatConstantsMatchServer.txt")
    inputs.file(serverCombatConstants).withPropertyName("serverCombatConstants")
    inputs.file(serverSkillConstants).withPropertyName("serverSkillConstants")
    inputs.file(serverSkillRegistry).withPropertyName("serverSkillRegistry")
    inputs.file(clientCombatConstants).withPropertyName("clientCombatConstants")
    outputs.file(stamp)
    doLast {
        val serverCombatText = serverCombatConstants.asFile.readText()
        val serverSkillText = serverSkillConstants.asFile.readText()
        val serverRegistryText = serverSkillRegistry.asFile.readText()
        val clientText = clientCombatConstants.asFile.readText()

        fun find1(regex: Regex, text: String, label: String): String =
            regex.find(text)?.groupValues?.get(1)
                ?: throw GradleException("Không tìm thấy $label — regex có thể đã lỗi thời so với file thật.")

        // Bốn hằng số đỉnh.
        val serverAttackInterval =
            find1(Regex("""ATTACK_INTERVAL_SECONDS\s*=\s*([\d.]+)f\s*;"""), serverCombatText, "ATTACK_INTERVAL_SECONDS (server)").toFloat()
        val serverAttackRange =
            find1(Regex("""ATTACK_RANGE_METERS\s*=\s*([\d.]+)f\s*;"""), serverCombatText, "ATTACK_RANGE_METERS (server)").toFloat()
        val serverMaxHp =
            find1(Regex("""CHARACTER_MAX_HP\s*=\s*(\d+)\s*;"""), serverCombatText, "CHARACTER_MAX_HP (server)").toInt()
        val serverMaxChi =
            find1(Regex("""CHARACTER_MAX_CHI\s*=\s*(\d+)\s*;"""), serverSkillText, "CHARACTER_MAX_CHI (server)").toInt()

        val clientAttackInterval =
            find1(Regex("""ATTACK_INTERVAL_SECONDS\s*=\s*([\d.]+)f\s*;"""), clientText, "ATTACK_INTERVAL_SECONDS (client)").toFloat()
        val clientAttackRange =
            find1(Regex("""ATTACK_RANGE_METERS\s*=\s*([\d.]+)f\s*;"""), clientText, "ATTACK_RANGE_METERS (client)").toFloat()
        val clientMaxHp =
            find1(Regex("""CHARACTER_MAX_HP\s*=\s*(\d+)\s*;"""), clientText, "CHARACTER_MAX_HP (client)").toInt()
        val clientMaxChi =
            find1(Regex("""CHARACTER_MAX_CHI\s*=\s*(\d+)\s*;"""), clientText, "CHARACTER_MAX_CHI (client)").toInt()

        val mismatches = mutableListOf<String>()
        fun compare(label: String, server: Any, client: Any) {
            if (server != client) mismatches.add("$label: server=$server, client=$client")
        }
        compare("ATTACK_INTERVAL_SECONDS", serverAttackInterval, clientAttackInterval)
        compare("ATTACK_RANGE_METERS", serverAttackRange, clientAttackRange)
        compare("CHARACTER_MAX_HP", serverMaxHp, clientMaxHp)
        compare("CHARACTER_MAX_CHI", serverMaxChi, clientMaxChi)

        // Hằng số Hỏa Cầu (SkillConstants) — nguồn thật mà cả SkillRegistry (slot 0 hệ Hỏa) lẫn
        // client FACTION_SKILLS đều chỉ THAM CHIẾU, không lặp literal.
        val serverFireballCode =
            find1(Regex("""FIREBALL_SKILL_CODE\s*=\s*"([a-z_]+)"\s*;"""), serverSkillText, "FIREBALL_SKILL_CODE (server)")
        val serverFireballCooldown =
            find1(Regex("""FIREBALL_COOLDOWN_SECONDS\s*=\s*([\d.]+)f\s*;"""), serverSkillText, "FIREBALL_COOLDOWN_SECONDS (server)").toFloat()
        val serverFireballChi =
            find1(Regex("""FIREBALL_CHI_COST\s*=\s*(\d+)\s*;"""), serverSkillText, "FIREBALL_CHI_COST (server)").toInt()

        val clientFireballCode =
            find1(Regex("""FIREBALL_SKILL_CODE\s*=\s*"([a-z_]+)"\s*;"""), clientText, "FIREBALL_SKILL_CODE (client)")
        val clientFireballCooldown =
            find1(Regex("""FIREBALL_COOLDOWN_SECONDS\s*=\s*([\d.]+)f\s*;"""), clientText, "FIREBALL_COOLDOWN_SECONDS (client)").toFloat()
        val clientFireballChi =
            find1(Regex("""FIREBALL_CHI_COST\s*=\s*(\d+)\s*;"""), clientText, "FIREBALL_CHI_COST (client)").toInt()

        // Token thô ở 1 tham số constructor có thể là literal ("thuy_song_du", 4f, 12) HOẶC tham
        // chiếu tới hằng số FIREBALL_* (chỉ hệ Hỏa) — giải nghĩa theo hình dạng token, không theo hệ.
        fun resolveCode(raw: String, fireball: String): String {
            val t = raw.trim()
            return if (t.startsWith("\"")) t.removeSurrounding("\"") else fireball
        }
        fun resolveCooldown(raw: String, fireball: Float): Float {
            val t = raw.trim()
            return if (t.endsWith("f")) t.dropLast(1).toFloat() else fireball
        }
        fun resolveChi(raw: String, fireball: Int): Int {
            val t = raw.trim()
            return t.toIntOrNull() ?: fireball
        }

        // Slot 0 (active đầu tiên) của mỗi hệ phái trong SkillRegistry.ALL — `new SkillDefinition(`
        // rồi code, Element.<X>, slotIndex 0 (literal — chỉ slot 0 mới bằng 0), targetKind,
        // scriptResource, cooldownSeconds, chiCost, đúng thứ tự tham số của SkillDefinition record.
        val skillEntry = Regex(
            """new SkillDefinition\(\s*([^,]+),\s*Element\.(HOA|THUY|PHONG|THO|LOI),\s*0,\s*""" +
                """SkillTargetKind\.\w+,\s*[^,]+,\s*([^,]+),\s*([^,]+),""")
        val serverSkills = mutableMapOf<String, Triple<String, Float, Int>>()
        skillEntry.findAll(serverRegistryText).forEach { m ->
            val element = m.groupValues[2]
            serverSkills[element] = Triple(
                resolveCode(m.groupValues[1], serverFireballCode),
                resolveCooldown(m.groupValues[3], serverFireballCooldown),
                resolveChi(m.groupValues[4], serverFireballChi))
        }

        // Cùng vai trò ở phía client — 1 dòng/hệ trong `FACTION_SKILLS = Map.of(...)`.
        val factionSkill = Regex(
            """Faction\.FACTION_(HOA|THUY|PHONG|THO|LOI),\s*new FactionSkill\(\s*([^,]+),\s*([^,]+),\s*([^,]+),""")
        val clientSkills = mutableMapOf<String, Triple<String, Float, Int>>()
        factionSkill.findAll(clientText).forEach { m ->
            val element = m.groupValues[1]
            clientSkills[element] = Triple(
                resolveCode(m.groupValues[2], clientFireballCode),
                resolveCooldown(m.groupValues[3], clientFireballCooldown),
                resolveChi(m.groupValues[4], clientFireballChi))
        }

        listOf("HOA", "THUY", "PHONG", "THO", "LOI").forEach { element ->
            val server = serverSkills[element]
                ?: throw GradleException("Server thiếu skill slot 0 hệ $element trong SkillRegistry.ALL.")
            val client = clientSkills[element]
                ?: throw GradleException("Client thiếu skill slot 0 hệ $element trong FACTION_SKILLS.")
            compare("$element skillCode", server.first, client.first)
            compare("$element cooldownSeconds", server.second, client.second)
            compare("$element chiCost", server.third, client.third)
        }

        if (mismatches.isNotEmpty()) {
            throw GradleException(
                "CombatConstants phía client lệch với server — HUD sẽ hiện nút skill sẵn " +
                    "sàng/hồi chiêu sai (không phải lỗ hổng, server vẫn tự kiểm, nhưng cần sửa " +
                    "CombatConstants.java phía client để khớp lại):\n  " + mismatches.joinToString("\n  ")
            )
        }
        stamp.get().asFile.writeText(
            "4 hằng số đỉnh + 5 skill hệ phái (code/cooldown/chi slot 0) khớp giữa client và server\n")
    }
}

tasks.named("check") { dependsOn(verifyCombatConstantsMatchServer) }

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
