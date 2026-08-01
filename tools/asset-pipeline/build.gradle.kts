// tools:asset-pipeline — ADR-029. Hạ mẫu @2x -> @1x/@0.5x, validate tên, đóng gói atlas 3 tier
// (low/med/high) qua gdx-tools TexturePacker, sinh asset-manifest.json, upsert art_asset (V19,
// ART-PIPELINE §8.3 — chạy sau khi V19__create_art_registry.sql được duyệt và áp dụng, 2026-08-01).
// KHÔNG chạy trong game (REPOSITORY-STRUCTURE.md §2 "tools/* — CLI build-time"). Dùng thẳng
// postgresql JDBC driver ở ĐÂY (không qua server-database) — Điều 23 "chỉ server-database chạm
// JDBC" nói về module SERVER (gameplay production), không áp cho tools/* build-time; ArchUnit rule
// chi_server_database_dung_jdbc cũng không quét module này (quality:architecture-tests không khai
// project dependency tới tools:asset-pipeline).
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(libs.gdx.tools)
    implementation(libs.postgresql)
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
