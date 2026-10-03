// quality:architecture-tests — ADR-011. Enforce dependency rule bằng ArchUnit (CI-CD §4).
// Quét class của mọi production module; vi phạm ranh giới = test fail = build fail.
plugins {
    // quality-conventions (không chỉ java-conventions): rà 2026-10-03 — module này là nơi DUY NHẤT
    // không cổng tĩnh nào quét (Checkstyle/PMD/SpotBugs/Spotless/Error Prone đều vắng), gài thử `var`
    // vào test của nó → xanh. Bật lên lộ 9 ConstantName (tên rule @ArchTest — suppress có lý do tại
    // lớp), 1 SpotBugs NP thật (getFileName() có thể null) và lệch format — đã sửa.
    id("kage.quality-conventions")
}

// Mọi module PRODUCTION (lá) dưới shared/engine/client/server — tự dò, KHÔNG liệt kê tay.
//
// Rà 2026-10-03: danh sách liệt kê tay cũ dừng ở feature-account/feature-character (Phase 3). 13
// feature module sau đó vẫn được quét chỉ nhờ MAY: chúng tới classpath gián tiếp qua server-gateway.
// client-desktop thì không — chưa từng bị ArchUnit nhìn thấy, mọi rule "xanh" với nó chỉ vì class của
// nó không có trên classpath test. Module mới thêm vào settings.gradle.kts giờ tự vào tầm quét.
// tools/ và quality/ không phải production nên đứng ngoài.
val productionRoots = listOf(":shared:", ":engine:", ":client:", ":server:")
val productionModules = rootProject.subprojects
    .filter { candidate -> productionRoots.any { candidate.path.startsWith(it) } }
    .filter { candidate -> candidate.file("build.gradle.kts").exists() && candidate.childProjects.isEmpty() }
    .map { it.path }
    .sorted()

dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")   // ✅ ADR-011

    // Phụ thuộc mọi production module để ArchUnit import được class của chúng.
    productionModules.forEach { testImplementation(project(it)) }
}

tasks.withType<Test>().configureEach {
    // ScannedModulesTest đối chiếu: mỗi module có mã nguồn main phải góp ít nhất một class vào tập quét.
    val sourceRoots = productionModules
        .map { rootProject.project(it).file("src/main/java") }
        .filter { it.isDirectory }
    systemProperty("kage.architecture.sourceRoots", sourceRoots.joinToString(File.pathSeparator) { it.absolutePath })
}
