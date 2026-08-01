# REPOSITORY-STRUCTURE.md

**Cấu trúc repository và quy tắc module**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 21–24](../00-governance/KAGE-CONSTITUTION.md#chương-v--nguyên-tắc-kiến-trúc) |

> Đây là hình dạng cụ thể của [ARCHITECTURE.md §8](ARCHITECTURE.md). Không tạo module ngoài cấu trúc này khi chưa có ADR.

---

## 1. Cây thư mục gốc

```
shinobi-engine/
├── CLAUDE.md                       # Quy tắc AI — Claude Code tự nạp
├── settings.gradle.kts
├── build-logic/                    # Convention plugins
│   └── src/main/kotlin/
│       ├── kage.java-conventions.gradle.kts
│       ├── kage.quality-conventions.gradle.kts
│       └── kage.test-conventions.gradle.kts
├── gradle/
│   └── libs.versions.toml          # Version Catalog — nguồn version DUY NHẤT
│
├── shared/
│   ├── shared-protocol/            # Packet/DTO (protobuf + Record wrapper)
│   └── shared-common/              # Constant, Common Model — KHÔNG business logic
│
├── engine/                         # "Kage Engine" — tái sử dụng, không biết gameplay cụ thể
│   ├── engine-core/                # Rendering, asset, screen/state lifecycle
│   ├── engine-ecs/                 # Bọc ECS framework (Ashley hôm nay, Kage ECS sau)
│   ├── engine-physics/             # Bọc Box2D
│   └── engine-scripting/           # Script sandbox runtime
│
├── client/
│   ├── client-core/                # Gameplay + UI đặc thù game, platform-agnostic
│   ├── client-desktop/             # Launcher LWJGL3
│   ├── client-android/             # Launcher Android
│   └── client-ios/                 # Launcher iOS (giai đoạn sau)
│
├── server/
│   ├── server-gateway/             # Netty: accept, auth, session, rate-limit, routing
│   ├── server-world/               # ECS server-side: movement, combat, AI, spawn
│   ├── server-database/            # Adapter PostgreSQL / Redis
│   └── server-features/
│       ├── feature-account/
│       ├── feature-character/
│       ├── feature-inventory/
│       ├── feature-dialogue/
│       ├── feature-guild/
│       ├── feature-quest/
│       ├── feature-shop/
│       ├── feature-mail/
│       ├── feature-friend/
│       ├── feature-chat/
│       ├── feature-auction/
│       └── feature-ranking/
│
├── tools/
│   ├── asset-pipeline/             # CLI export/pack/validate asset
│   ├── content-editor/             # Editor quest/item/skill (Phase xa — xem EDITOR.md)
│   └── load-test/                  # Client giả lập qua socket thật (BENCHMARK.md §5, Phase 9 lát cắt 2)
│
├── assets/
│   ├── hyperlap2d-projects/        # Project gốc — KHÔNG đóng gói vào game
│   ├── raw/                        # Art nguồn (PSD/AI) — KHÔNG đóng gói vào game
│   └── exported/                   # Runtime asset — đóng gói vào game
│
├── deploy/
│   ├── docker/
│   └── k8s/                        # Chỉ tạo khi ADR chốt cần K8s
│
└── docs/
```

Mỗi thư mục cấp 2 trở lên là **một Gradle module** (`:engine:engine-core`, `:server:server-features:feature-inventory`).

---

## 2. Trách nhiệm từng module

Điều 22 — một module, một trách nhiệm.

| Module | Trách nhiệm **duy nhất** | Được phụ thuộc |
|---|---|---|
| `shared-protocol` | Định nghĩa packet/DTO | *(không phụ thuộc module nào trong repo)* |
| `shared-common` | Constant, Common Model | `shared-protocol` |
| `engine-core` | Rendering, asset, vòng đời màn hình | libGDX, `shared-common` |
| `engine-ecs` | Bọc ECS framework, cung cấp API nội bộ ổn định | Ashley, `shared-common` |
| `engine-physics` | Bọc Box2D | Box2D, `engine-ecs` |
| `engine-scripting` | Chạy script sandbox | `engine-ecs` |
| `client-core` | Gameplay client + Scene2D UI | `engine-*`, `shared-protocol` |
| `client-desktop/android/ios` | **Chỉ** Launcher + platform adapter | `client-core` |
| `server-gateway` | Nhận kết nối, xác thực, routing packet | Netty, `shared-protocol` |
| `server-world` | ECS simulation phía server | `engine-ecs`, `shared-protocol` |
| `server-database` | Adapter DB/cache. **KHÔNG business logic** | JDBC, Redis client |
| `feature-*` | Một nghiệp vụ, theo Clean Architecture | `shared-protocol`, Port của chính nó |
| `tools/*` | CLI build-time. **Không chạy trong game** | tuỳ tool |

---

## 3. Dependency Rules — kiểm chứng bằng máy

Điều 23. Đây **không phải khuyến nghị** — phải cấu hình Gradle để vi phạm = build fail.

| Module | ❌ Cấm import |
|---|---|
| `engine-*`, `client-core`, `server-world` | `io.netty.*`, `org.postgresql.*`, `redis.clients.*` / `io.lettuce.*`, `org.springframework.*` |
| Mọi module ngoài `server-gateway` | `io.netty.*` |
| Mọi module ngoài `server-database` | JDBC, Redis client |
| `shared-*` | Bất kỳ business logic nào |
| `<feature>/domain/` | `<feature>/infrastructure/` |
| `client-core` | API platform cụ thể (Android SDK, RoboVM) |

**Cách enforce**: cần ADR-011 chốt cơ chế (Gradle `dependencies` constraint / ArchUnit test / custom plugin). Triển khai ở Phase 0.

---

## 4. Cấu trúc bên trong một feature module

Điều 24 — package theo **feature**, không theo **loại**.

```
feature-inventory/
└── src/main/java/dev/kage/inventory/
    ├── api/
    │   └── InventoryFacade.java          # Public API — module khác CHỈ gọi qua đây
    ├── application/
    │   ├── AddItemUseCase.java
    │   ├── MoveItemUseCase.java
    │   └── EquipItemUseCase.java
    ├── domain/
    │   ├── Inventory.java                 # Aggregate root
    │   ├── ItemStack.java                 # Value Object (record)
    │   ├── EnhanceLevel.java              # Value Object
    │   └── InventoryPort.java             # Port — interface, không biết DB
    └── infrastructure/
        ├── PostgresInventoryAdapter.java  # implements InventoryPort
        └── RedisInventoryCacheAdapter.java
```

```
api → application → domain ← infrastructure
```

`domain/` là tâm, không import ra ngoài. `infrastructure/` phụ thuộc ngược vào `domain/` để implement Port.

---

## 5. Naming Rules

| Đối tượng | Quy tắc | ✅ Đúng | ❌ Sai |
|---|---|---|---|
| Module Gradle | `kebab-case`, có tiền tố nhóm | `feature-inventory` | `inventory`, `InventoryModule` |
| Package root | `dev.kage.<feature>` | `dev.kage.inventory` | `com.game.inv` |
| Facade | `<Feature>Facade` | `InventoryFacade` | `InventoryService`, `InventoryManager` |
| Use case | `<Verb><Noun>UseCase` | `AddItemUseCase` | `InventoryHandler` |
| Port | `<Feature>Port` | `InventoryPort` | `IInventoryRepository` |
| Adapter | `<Tech><Feature>Adapter` | `PostgresInventoryAdapter` | `InventoryDaoImpl` |
| Constant | `<Domain>Constants` | `CombatConstants` | `Constants`, `Const` |

**Cấm tên module/class chung chung**: `util`, `common-utils`, `misc`, `helper`, `manager`, `base`, `data`, `info` — trừ khi phản ánh đúng trách nhiệm (Điều 22).

---

## 6. Package root — ✅ `dev.kage` ([ADR-012](adr/ADR-012-Package-Root.md))

Package root chính thức: **`dev.kage`**. Đây là định danh kỹ thuật nội bộ — **tên thương hiệu game đổi sau không đụng package** (xem [ADR-012](adr/ADR-012-Package-Root.md) để hiểu vì sao tách hai khái niệm).

---

## 7. Quy trình thêm module mới

```
1. Module mới thuộc nhóm nào? (engine- / client- / server- / feature- / tools/)
2. Trách nhiệm có trùng module nào ở §2 không?  → Trùng thì KHÔNG tạo, mở rộng module cũ.
3. Dependency direction có vi phạm §3 không?
4. Cần ADR không?  → Thêm/xoá/đổi tên module: LUÔN cần (Điều 36).
5. ADR Accepted → thêm vào settings.gradle.kts + bảng §2 tài liệu này (cùng PR).
```

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Multi-module Gradle, nhóm theo tiền tố (`engine-`, `client-`, `server-`, `feature-`) | Accepted |
| 2 | `feature-*` nằm dưới `server/server-features/` (feature là nghiệp vụ server-side) | Accepted |
| 3 | Dependency rule phải enforce bằng build tool, không bằng văn bản | Accepted — cần ADR-011 |
| 4 | Package root `dev.kage.*` | ✅ [ADR-012](adr/ADR-012-Package-Root.md) |
| 5 | `deploy/k8s/` chỉ tạo khi ADR chốt cần K8s | Accepted |

## ADR cần tạo

| ADR | Nội dung | Trạng thái |
|---|---|---|
| **ADR-011** | Cơ chế enforce dependency rule (§3) | ❌ Cần trước Phase 0 |
| **ADR-012** | Package root `dev.kage` (§6) | ✅ Accepted |

## Tài liệu liên quan

- [ARCHITECTURE.md](ARCHITECTURE.md) — §8 module dependency graph
- [TECHNOLOGY-STACK.md](TECHNOLOGY-STACK.md) — thư viện nào cho module nào
- [CODING-STANDARDS.md](../02-coding-standards/CODING-STANDARDS.md) — naming ở mức class/method
- [ROADMAP.md](../11-process/ROADMAP.md) — Phase 0 tạo scaffold theo §1

## Việc cần làm tiếp theo

1. ✅ Package root `dev.kage` đã chốt (ADR-012).
2. Chốt cơ chế enforce §3 (ADR-011).
3. Phase 0: tạo scaffold đúng §1, module rỗng nhưng `./gradlew build` phải xanh.
