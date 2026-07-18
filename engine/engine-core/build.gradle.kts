// engine-core — rendering, asset, Screen/State (chỉ client dùng — ENGINE.md §9).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":shared:shared-common"))
    // api(libs.bundles.gdx.desktop) — thêm khi render loop đầu tiên (Phase 1).
}
