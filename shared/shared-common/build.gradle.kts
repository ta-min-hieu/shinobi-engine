// shared-common — Constant, Common Model. KHÔNG business logic (Điều 22).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":shared:shared-protocol"))
}
