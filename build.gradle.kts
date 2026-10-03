// Root build — cố ý TỐI THIỂU. Cấu hình thật nằm ở build-logic/ convention plugins
// và từng module. Root không khai báo dependency, không apply plugin cho allprojects
// (tránh coupling ngầm — Điều 12 Explicit over Implicit).
//
// ⚠️ NGOẠI LỆ BẮT BUỘC (2026-07-30, BUILD.md "Bước 3"): `apply false` ở đây KHÔNG áp dụng
// Spotless cho root project (root vẫn không có hành vi gì) — chỉ ép Gradle resolve/load class
// Spotless đúng 1 LẦN DUY NHẤT ở classloader gốc. Thiếu dòng này, mỗi subproject áp
// `kage.quality-conventions` (qua precompiled script plugin) với tập plugin tổng thể KHÁC NHAU
// (vd `engine-ecs` có thêm `jmh`, `client-core` có thêm libGDX) khiến Gradle tạo classloader
// RIÊNG cho Spotless ở từng project — `SpotlessTaskService` (build service dùng chung) thấy 2
// Class khác nhau cho cùng 1 type → build fail thật
// ("Cannot set the value of task ... property 'taskService' ... loaded with
// InstrumentingVisitableURLClassLoader..."), xác nhận qua chạy `./gradlew build` thật, không suy
// đoán. Đây là giới hạn kỹ thuật đã biết của Spotless với convention plugin đa module (xem
// diffplug/spotless #747, gradle/gradle #17559) — không phải lựa chọn tuỳ ý, không mở rộng thêm
// plugin khác vào đây trừ khi gặp đúng lỗi tương tự.
plugins {
    alias(libs.plugins.spotless) apply false
}

tasks.register("printModules") {
    group = "help"
    description = "Liệt kê module đã include (kiểm tra scaffold Phase 0)."
    doLast {
        subprojects.forEach { println(it.path) }
    }
}

// Cổng: mọi project trong settings.gradle.kts phải có dòng COPY build.gradle.kts riêng trong
// deploy/docker/server.Dockerfile. Dockerfile chạy `./gradlew :server:server-gateway:dependencies`
// với settings.gradle.kts ĐẦY ĐỦ (không settings rút gọn, không configure-on-demand) TRƯỚC `COPY . .`
// — Gradle configure MỌI project đã include, kể cả client/tools không vào image, nên thiếu thư mục
// của một module là Gradle 9 từ chối ("Configuring project ':x' without an existing directory is
// not allowed"). Source module thì không cần ở bước đó: `COPY . .` sau nó mang toàn bộ cây vào.
// Đã quên dòng COPY HAI lần (2026-08-01; feature-monster 2026-08-30 + feature-world 2026-09-07,
// sửa ở 15759c6) ⇒ image không build được hàng tuần mà không ai biết — job docker chỉ chạy trên
// master. Cổng này chỉ đọc file, không cần Docker.
val verifyDockerfileModules = tasks.register("verifyDockerfileModules") {
    group = "verification"
    description = "Mọi include(...) trong settings.gradle.kts phải có dòng COPY build.gradle.kts trong server.Dockerfile."
    val settingsFile = layout.projectDirectory.file("settings.gradle.kts").asFile
    val dockerfile = layout.projectDirectory.file("deploy/docker/server.Dockerfile").asFile
    val stamp = layout.buildDirectory.file("verifyDockerfileModules.txt")
    inputs.files(settingsFile, dockerfile).withPropertyName("settingsAndDockerfile")
    outputs.file(stamp)
    doLast {
        // Bỏ comment `/* */` và `//` trước khi tìm — module bị comment-out không phải module thật.
        // `\binclude\(` không khớp `includeBuild(` (sau "include" là "B", không phải "(").
        val settings = settingsFile.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
        if (settings.contains("projectDir")) {
            throw GradleException(
                "settings.gradle.kts đổi projectDir — cổng này giả định thư mục = đường dẫn project " +
                    "(':a:b' → a/b); sửa cổng trước khi tin nó"
            )
        }
        val included = sortedSetOf<String>()
        Regex("""\binclude\(([^)]*)\)""").findAll(settings).forEach { call ->
            Regex(""""(:[^"]+)"""").findAll(call.groupValues[1]).forEach {
                included.add(it.groupValues[1].removePrefix(":").replace(':', '/'))
            }
        }
        // `COPY <dir>/build.gradle.kts <dir>/` — đích phải trùng thư mục nguồn, lệch là dựng sai chỗ.
        val copyLine = Regex("""^COPY\s+(\S+)/build\.gradle\.kts\s+(\S+?)/?\s*$""")
        val copied = sortedSetOf<String>()
        val wrongTarget = mutableListOf<String>()
        dockerfile.readLines().map { it.trim() }.filter { !it.startsWith("#") }.forEach { line ->
            copyLine.matchEntire(line)?.let {
                val dir = it.groupValues[1]
                copied.add(dir)
                if (it.groupValues[2] != dir) wrongTarget.add("$line (đích phải là $dir/)")
            }
        }
        // Chốt ngược: regex hỏng hoặc file dời chỗ thì một trong hai tập rỗng và cổng XANH mà không
        // kiểm gì. Repo luôn có hàng chục module — rỗng chắc chắn là cổng hỏng.
        if (included.isEmpty() || copied.isEmpty()) {
            throw GradleException(
                "verifyDockerfileModules trích rỗng (include: ${included.size}, COPY build.gradle.kts: " +
                    "${copied.size}) — regex hoặc đường dẫn đã lệch, cổng này sẽ IM LẶNG vô dụng"
            )
        }
        val missing = included.minus(copied)
        val stale = copied.minus(included)
        if (missing.isNotEmpty() || stale.isNotEmpty() || wrongTarget.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("deploy/docker/server.Dockerfile lệch settings.gradle.kts — image server sẽ không build được:")
                    missing.forEach {
                        append("\n  thiếu: COPY $it/build.gradle.kts $it/   (module :${it.replace('/', ':')})")
                    }
                    stale.forEach { append("\n  thừa (không còn trong settings, COPY sẽ fail): $it/build.gradle.kts") }
                    wrongTarget.forEach { append("\n  sai đích: $it") }
                }
            )
        }
        stamp.get().asFile.writeText("${included.size} module trong settings, đủ dòng COPY build.gradle.kts\n")
    }
}

// Root cố ý không apply plugin nào (kể cả `base`), nên không có task `check` — đăng ký một task
// lifecycle cùng tên để `./gradlew check` (chạy `check` của mọi project) kéo cổng này theo.
tasks.register("check") {
    group = "verification"
    description = "Cổng kiểm cấp root (Dockerfile ↔ settings)."
    dependsOn(verifyDockerfileModules)
}
