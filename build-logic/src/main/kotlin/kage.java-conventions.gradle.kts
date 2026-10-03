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
    // -Xlint:all + -Werror: warning của javac VÀ của Error Prone (check mức WARNING) đều làm đỏ build.
    //
    // Rà 2026-10-03: trước đây CHỦ ĐÍCH không -Werror, với lý do "thư viện thứ ba (Ashley/libGDX/
    // Netty) sinh warning không sửa được". Đo thật (`compileJava compileTestJava compileJmhJava
    // --rerun-tasks`, 28 module): 0 warning — warning -Xlint chỉ báo trên mã nguồn CỦA TA (vd lời gọi
    // varargs generic thì @SafeVarargs/@SuppressWarnings ngay tại chỗ gọi), không có warning nào
    // "của người khác". Không -Werror thì luật "warning = 0" (Definition of Done, CLAUDE.md §10) chỉ
    // nằm trong trí nhớ: gài thử một raw type vào engine-core → build vẫn XANH, warning trôi qua log.
    // Warning thật sự không tránh được → @SuppressWarnings("<lint-key>") hẹp nhất + comment lý do.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
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
