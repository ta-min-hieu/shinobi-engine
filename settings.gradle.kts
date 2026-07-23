// settings.gradle.kts — cấu trúc module Project Kage (REPOSITORY-STRUCTURE.md §1)
pluginManagement {
    // Convention plugins nằm ở included build build-logic/ (build-logic/ được chú thích rõ)
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // libGDX Box2D/native đôi khi cần Sonatype snapshots — thêm khi cần, không thêm sẵn.
    }
    // Version Catalog tự nạp từ gradle/libs.versions.toml (không cần khai báo).
}

rootProject.name = "shinobi-engine"

// ---- shared ----
include(":shared:shared-common")
include(":shared:shared-protocol")

// ---- engine (dùng chung client + server; engine-ecs KHÔNG phụ thuộc engine-core) ----
include(":engine:engine-core")
include(":engine:engine-ecs")
include(":engine:engine-physics")
include(":engine:engine-scripting")
include(":engine:engine-plugin-api")

// ---- client ----
include(":client:client-core")
include(":client:client-desktop")
// client-android / client-ios: THÊM khi bắt đầu backend mobile (cần AGP/RoboVM + SDK).
//   Không scaffold sẵn (Điều 9 — tránh module nửa vời cần SDK ngoài). Xem client/README.md.

// ---- server ----
include(":server:server-gateway")
include(":server:server-world")
include(":server:server-database")
// server-features/feature-*: THÊM từ docs/templates/feature-module/ khi tới đúng Phase (theo
//   ROADMAP.md, không phải comment cũ dưới đây — feature-account/feature-character = Phase 3,
//   feature-inventory/feature-shop = Phase 5, phần còn lại = Phase 6-7). Không scaffold 11 module
//   rỗng trước (Điều 9 / YAGNI). Xem server/server-features/README.md.
include(":server:server-features:feature-account")
include(":server:server-features:feature-character")
// feature-inventory mở sớm hơn ROADMAP dự kiến (Phase 5) — quyết định của chủ dự án cho lát cắt
// "rơi đồ thật vào túi đồ" ở Phase 4, xem kage_phase4_state trong memory AI.
include(":server:server-features:feature-inventory")
include(":server:server-features:feature-shop")

// ---- quality ----
include(":quality:architecture-tests")   // ArchUnit (ADR-011) — enforce dependency rule

// ---- tools ----
// tools:asset-pipeline: THÊM ở Phase 1 (RESOURCE.md). Không scaffold trước.
