// build-logic — biên dịch các precompiled script plugin (kage.*-conventions).
plugins {
    `kotlin-dsl`
}

dependencies {
    // BUILD.md "Bước 3" (2026-07-30) — version khớp [plugins]/[versions] gradle/libs.versions.toml,
    // verify thật trước khi ghi (không copy số cũ — CLAUDE.md).
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.9.0")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.11")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
}
