// build-logic — included build chứa convention plugin dùng chung (TECHNOLOGY-STACK.md §3).
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // Cho phép convention plugin đọc version catalog gốc của dự án.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
