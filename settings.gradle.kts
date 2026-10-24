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
        // Kho Maven cục bộ nằm trong repo (ADR-032). Chỉ chứa gdx-gltf, thứ duy
        // nhất dự án cần mà Maven Central không có. Đóng gói thay vì khai
        // jitpack.io: JitPack build theo yêu cầu từ GitHub, nên repo gốc bị xoá
        // là build gãy vĩnh viễn — rủi ro checksum không chặn được.
        // Xem third-party/m2/README.md.
        maven { url = uri(rootDir.resolve("third-party/m2")) }
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
include(":server:server-features:feature-dialogue")
include(":server:server-features:feature-quest")
include(":server:server-features:feature-friend")
include(":server:server-features:feature-mail")
include(":server:server-features:feature-chat")
include(":server:server-features:feature-guild")
include(":server:server-features:feature-party")
include(":server:server-features:feature-trade")
include(":server:server-features:feature-pvp")
include(":server:server-features:feature-monster")
include(":server:server-features:feature-world")   // ADR-046 D3 — hình học bản đồ (safe_zone)
include(":server:server-features:feature-achievement") // ADR-062 — thành tựu + danh hiệu
include(":server:server-features:feature-checkin")
include(":server:server-features:feature-crafting")
include(":server:server-features:feature-market")     // ADR-063 — phường thị // ADR-060 — điểm danh hằng ngày
include(":server:server-features:feature-gm")      // ADR-052 — lệnh GM trong game (quyền + audit)

// ---- quality ----
include(":quality:architecture-tests")   // ArchUnit (ADR-011) — enforce dependency rule

// ---- tools ----
include(":tools:asset-pipeline")   // ADR-029 — hạ mẫu + đóng gói atlas + sinh manifest
include(":tools:load-test")   // BENCHMARK.md §5 — Phase 9 lát cắt 2, client giả lập qua socket thật
