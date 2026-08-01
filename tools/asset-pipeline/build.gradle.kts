// tools:asset-pipeline — ADR-029. Hạ mẫu @2x -> @1x/@0.5x, validate tên, đóng gói atlas 3 tier
// (low/med/high) qua gdx-tools TexturePacker, sinh asset-manifest.json. KHÔNG chạy trong game
// (REPOSITORY-STRUCTURE.md §2 "tools/* — CLI build-time"). Phạm vi v1 KHÔNG gồm upsert DB
// art_asset (ADR-029 — chờ V19__create_art_registry.sql được duyệt riêng).
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(libs.gdx.tools)
}
application {
    mainClass.set("dev.kage.tools.assetpipeline.AssetPipelineRunner")
}
tasks.named<JavaExec>("run") {
    // ⚠️ PHÁT HIỆN THẬT: console Windows mặc định không phải UTF-8 — log tiếng Việt của tool này
    // (println thẳng, không qua logger) ra chữ mojibake dù nguồn UTF-8 đúng. JDK 18+ mới có
    // property stdout.encoding/stderr.encoding để ép console output UTF-8 không cần đổi code.
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
