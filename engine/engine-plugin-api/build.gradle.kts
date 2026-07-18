// engine-plugin-api — interface Plugin (giữ chỗ; plugin system hoãn sau Phase 6 — ADR-007).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-ecs"))
    api(project(":shared:shared-common"))
}
