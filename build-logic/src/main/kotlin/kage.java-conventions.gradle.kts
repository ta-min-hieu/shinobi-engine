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
    // -Werror BẬT từ 2026-10-17 (dòng 58(a), chủ dự án duyệt): rà 2026-10-03 đo 0 warning trên 28
    // module, nên lo ngại cũ ("warning của thư viện thứ ba chặn cả build", STATIC-ANALYSIS.md §11)
    // không xảy ra thật — warning của javac chỉ nói về code CỦA TA. Giữ mức 0 đó bằng máy thay vì
    // bằng trí nhớ. Một warning không sửa được thì @SuppressWarnings tại chỗ, kèm lý do.
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-Werror")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Ngân sách RAM (2026-10-23): máy dev tối đa 32GB. Không đặt thì JVM test lấy trần heap mặc định
    // = 1/4 RAM vật lý (8GB) và GC cứ để heap phình tới đó; vài JVM test + SpotBugs chạy song song
    // là đủ làm máy cạn RAM (lần `check` toàn repo 2026-10-21 bị giết đúng vì vậy). Tổng ngân sách ở
    // gradle.properties gốc.
    maxHeapSize = "1g"
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
