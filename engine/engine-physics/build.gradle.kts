// engine-physics — bọc Box2D. Va chạm/môi trường, KHÔNG combat (PHYSICS.md §1).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-ecs"))
    // api(libs.gdx.box2d) — thêm ở Phase 3.
}
