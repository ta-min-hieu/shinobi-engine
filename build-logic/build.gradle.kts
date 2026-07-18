// build-logic — biên dịch các precompiled script plugin (kage.*-conventions).
plugins {
    `kotlin-dsl`
}

dependencies {
    // Phase 0: convention plugin chỉ dùng plugin BUILT-IN (checkstyle, pmd) → không cần dep ở đây.
    //
    // Khi wire Spotless / SpotBugs / Error Prone (bước tiếp theo sau khi build đầu xanh),
    // bỏ comment các dòng dưới. ⚠️ Version phải khớp [plugins] trong gradle/libs.versions.toml.
    // implementation("com.diffplug.spotless:spotless-plugin-gradle:7.2.1")
    // implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.9")
    // implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
}
