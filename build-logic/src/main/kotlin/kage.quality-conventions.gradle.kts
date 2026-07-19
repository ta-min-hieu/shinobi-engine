// kage.quality-conventions — static analysis (STATIC-ANALYSIS.md).
// Áp plugin này = có luôn java-conventions (toolchain 25 + test) + Checkstyle + PMD.
//
// Checkstyle & PMD là plugin BUILT-IN của Gradle → accessor ổn định, rủi ro thấp.
// Spotless / SpotBugs / Error Prone (bên thứ ba) CHƯA wire ở đây — xem README Phase 0:
//   chúng cần iterate ở lần build thật (accessor precompiled-script + tương thích Gradle 9).
//   Enforcement cốt lõi Phase 0 = Checkstyle + PMD + ArchUnit + javac -Werror, đã đủ chặn drift.
plugins {
    id("kage.java-conventions")
    checkstyle
    pmd
}

checkstyle {
    toolVersion = "10.21.0"   // ⚠️ sync libs.versions.toml
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false  // vi phạm = build fail (Điều 41)
}

// ⚠️ PHÁT HIỆN THẬT: rule MethodName của bản main cấm dấu "_", nhưng TESTING.md §10 khuyến
// nghị đúng quy ước "shouldX_whenY" cho TÊN TEST (mô tả hành vi rõ hơn thuần camelCase). Test
// task dùng config riêng (checkstyle-test.xml) chỉ khác đúng 1 rule đó — không nới lỏng gì khác.
tasks.named<Checkstyle>("checkstyleTest") {
    configFile = rootProject.file("config/checkstyle/checkstyle-test.xml")
}

pmd {
    toolVersion = "7.10.0"    // ⚠️ sync
    isConsoleOutput = true
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    ruleSets = emptyList()    // chỉ ruleset dự án
}
