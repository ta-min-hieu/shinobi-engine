import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort

// kage.quality-conventions — static analysis (STATIC-ANALYSIS.md).
// Áp plugin này = có luôn java-conventions (toolchain 25 + test) + Checkstyle + PMD + Spotless +
// SpotBugs(+find-sec-bugs) + Error Prone.
//
// BUILD.md "Bước 3" (2026-07-30) — wire nốt 3 tool bên thứ ba từng cố ý hoãn ở Phase 0 để build
// đầu chắc xanh (chỉ Checkstyle + PMD + ArchUnit + -Werror lúc đó). Version literal ở dưới KHÔNG
// dùng accessor `libs.*` — build-logic là included build riêng, không share version catalog gốc
// (giống pattern có sẵn ở checkstyle/pmd toolVersion ngay trên, luôn kèm "sync libs.versions.toml").
plugins {
    id("kage.java-conventions")
    checkstyle
    pmd
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
    id("net.ltgt.errorprone")
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
    toolVersion = "7.26.0"    // ⚠️ sync — bump từ 7.10.0, fix StackOverflowError type-inference JDK 25
    isConsoleOutput = true
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    ruleSets = emptyList()    // chỉ ruleset dự án
}

// STATIC-ANALYSIS.md §4 — format là quyết định 1 lần, không tranh luận PR. `./gradlew spotlessApply`
// sửa hết. palantirJavaFormat() (không phải googleJavaFormat — doc để "hoặc", chọn palantir làm
// mặc định vì đây là lựa chọn đầu tiên doc liệt kê).
// ⚠️ PHÁT HIỆN THẬT: mặc định Spotless quét CẢ `build/generated/sources/proto/**` (protoc tự sinh,
// shared-protocol) — 382 file generated fail format check (khác style Google tự sinh, không phải
// code ta viết, không nên/không thể "sửa" vì protoc sẽ ghi đè lại y hệt lần build sau).
// targetExclude loại thư mục generated khỏi phạm vi Spotless.
spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        palantirJavaFormat()
        importOrder("java", "javax", "", "dev.kage")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// STATIC-ANALYSIS.md §7 — find-sec-bugs BẮT BUỘC (ADR-004: không ORM → tự viết SQL → không có
// framework che SQL injection, SECURITY.md §11).
spotbugs {
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}
dependencies {
    "spotbugsPlugins"("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")  // ⚠️ sync libs.versions.toml
}

// STATIC-ANALYSIS.md §6 — Error Prone bắt bug lúc compile.
// ⚠️ PHÁT HIỆN THẬT (2026-07-30): check tên "AutoBoxing" mà STATIC-ANALYSIS.md §6 nhắc tới KHÔNG
// TỒN TẠI trong Error Prone thật (`AutoBoxing is not a valid checker name` lúc build) — không có
// check chung "phát hiện MỌI autoboxing" trong Error Prone, chỉ có vài check hẹp hơn LIÊN QUAN đến
// boxed primitive, đã BẬT SẴN ở severity mặc định của tool (không cần promote thêm):
// BoxedPrimitiveEquality (ERROR), LockOnBoxedPrimitive (ERROR), IdentityHashMapBoxing (ERROR),
// BoxedPrimitiveConstructor/BoxingComparator/ObjectEqualsForPrimitives (WARNING). Đã sửa
// STATIC-ANALYSIS.md để không lặp lại tên check sai này. KHÔNG promote thêm check nào khác ở đây
// (tránh đúng cái bẫy "5000 warning không ai fix" mà chính STATIC-ANALYSIS.md §11 cảnh báo) —
// boxing trong hot path (Điều 31) vẫn chủ yếu dựa vào review + JMH benchmark (BENCHMARK.md), không
// có 1 check tĩnh nào bắt được toàn bộ trường hợp.
dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.50.0")  // ⚠️ sync libs.versions.toml
}
