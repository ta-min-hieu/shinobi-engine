// engine-core — rendering, asset, Screen/State (chỉ client dùng — ENGINE.md §9).
// engine-core -> engine-ecs là chiều HỢP LỆ (chỉ chiều ngược lại bị cấm — ENGINE.md §9).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":shared:shared-common"))
    api(project(":engine:engine-ecs"))
    api("com.badlogicgames.gdx:gdx:1.14.2")
}
