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
}
