// server-world — ECS simulation phía server: movement, combat, AI (SERVER.md).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-ecs"))
    api(project(":engine:engine-physics"))
    api(project(":shared:shared-protocol"))
}
