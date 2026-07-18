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
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Werror"                     // warning = lỗi (Definition of Done: không warning)
    ))
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
