// kage.java-conventions — nền chung mọi module Java (CODING-STANDARDS, JAVA-GUIDELINES).
plugins {
    `java-library`
}

group = "dev.kage"                    // ADR-012

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))   // ADR-001
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // ⚠️ CHỦ ĐÍCH KHÔNG -Werror: dự án bọc thư viện thứ ba (Ashley/libGDX/Netty) — chúng phát
    // sinh warning (VD varargs generic heap-pollution) không phải lỗi của code ta và không sửa
    // được. -Werror chặn TOÀN BỘ build vì warning của người khác = "quá nghiêm → bị tắt hẳn"
    // (STATIC-ANALYSIS.md §11). Vẫn giữ -Xlint:all để warning HIỂN THỊ, không ẩn đi.
    // Enforcement thật nằm ở Checkstyle + PMD + ArchUnit (nhắm đúng code CỦA TA).
    // Rà 2026-10-03 đề xuất bật -Werror (đo 0 warning trên 28 module) — CHỜ CHỦ DỰ ÁN, xem
    // STATIC-ANALYSIS.md §8.1; quyết định này không tự đảo.
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// JUnit 5 + AssertJ cho mọi module (test-conventions mở rộng thêm nếu cần).
dependencies {
    "testImplementation"(platform("org.junit:junit-bom:5.14.4"))   // ⚠️ sync libs.versions.toml
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.assertj:assertj-core:3.27.6")        // ⚠️ verify
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
