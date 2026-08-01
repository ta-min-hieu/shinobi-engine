// quality:architecture-tests — ADR-011. Enforce dependency rule bằng ArchUnit (CI-CD §4).
// Quét class của mọi production module; vi phạm ranh giới = test fail = build fail.
plugins {
    id("kage.java-conventions")
}

dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")   // ✅ ADR-011

    // Phụ thuộc mọi production module để ArchUnit import được class của chúng.
    // (Module rỗng ở Phase 0 → chưa có class → rule pass trivially, nhưng đã sẵn để "bite".)
    testImplementation(project(":shared:shared-common"))
    testImplementation(project(":shared:shared-protocol"))
    testImplementation(project(":engine:engine-core"))
    testImplementation(project(":engine:engine-ecs"))
    testImplementation(project(":engine:engine-physics"))
    testImplementation(project(":engine:engine-scripting"))
    testImplementation(project(":client:client-core"))
    testImplementation(project(":server:server-gateway"))
    testImplementation(project(":server:server-world"))
    testImplementation(project(":server:server-database"))
    testImplementation(project(":server:server-features:feature-account"))
    testImplementation(project(":server:server-features:feature-character"))
}
