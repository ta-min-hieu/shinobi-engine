// engine-ecs — bọc ECS framework. NƠI DUY NHẤT được import com.badlogic.ashley.* (ADR-006).
// KHÔNG phụ thuộc engine-core (server không được kéo theo rendering — ENGINE.md §9).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":shared:shared-common"))
    // api(libs.gdx.ashley) — thêm khi viết code ECS đầu tiên (Phase 1).
}
