// engine-scripting — sandbox chạy script (Lua/GraalJS — chờ ADR-016). Chỉ server (Điều 15).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-ecs"))
    // Script engine dep thêm sau khi ADR-016 chốt (Phase 2).
}
