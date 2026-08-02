// client-core — gameplay client + UI, platform-agnostic (CLIENT.md). Client chỉ hiển thị (Điều 15).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-core"))
    api(project(":engine:engine-ecs"))
    api(project(":engine:engine-physics"))
    api(project(":shared:shared-protocol"))
    // Chỉ API — binding thật (nếu cần) do launcher (client-desktop) quyết định, không phải
    // gameplay code (giữ client-core platform-agnostic, đúng tinh thần CLIENT.md).
    implementation("org.slf4j:slf4j-api:2.0.18")
    // FreeType sinh font runtime từ .ttf (RESOURCE.md §7) — đã duyệt sẵn trong TECHNOLOGY-STACK.md,
    // chỉ mới kích hoạt thật ở đây (không phải thư viện mới theo CLAUDE.md §4). gdx-freetype thuần
    // Java, không cần native platform jar ở module này — native chỉ cần ở nơi thật sự chạy app
    // (client-desktop), theo đúng cách gdx-platform natives-desktop đã làm.
    api(libs.gdx.freetype)
    // Test cần native FreeType thật để generateFont()/hasGlyph() chạy (FontFactoryVietnameseGlyphTest)
    // — test JVM chạy trực tiếp trên desktop, không qua client-desktop, nên cần natives ở đây.
    testImplementation(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}
