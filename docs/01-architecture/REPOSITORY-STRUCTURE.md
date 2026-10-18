# REPOSITORY-STRUCTURE.md

**Cấu trúc repository và quy tắc module**

| | |
|---|---|
| Version | 1.1.0 |
| Status | Draft — §1 rà lại theo thực tế 2026-09-06 |
| Luật gốc | [Hiến pháp Điều 21–24](../00-governance/KAGE-CONSTITUTION.md#chương-v--nguyên-tắc-kiến-trúc) |

> Đây là hình dạng cụ thể của [ARCHITECTURE.md §8](ARCHITECTURE.md). Không tạo module ngoài cấu trúc này khi chưa có ADR.

---

## 1. Cây thư mục gốc

> **Cây dưới đây là THỰC TẾ** — mọi module ở đây đều có trong `settings.gradle.kts` và build được
> ngay hôm nay (rà lại 2026-09-06). Module *dự kiến* nằm riêng ở [§1.1](#11-module-dự-kiến-chưa-tồn-tại)
> để không ai đọc nhầm kế hoạch thành hiện trạng — chính chỗ lệch đó đã giữ một ô Exit Criteria của
> Phase 0 trống suốt từ 2026-08-30.

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
├── third-party/m2/                 # Repo Maven cục bộ cho dependency vendored (ADR-032)
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
│   └── client-desktop/             # Launcher LWJGL3 — sơ đồ điều khiển CHÍNH (ADR-023 desktop-first)
│
├── server/
│   ├── server-gateway/             # Netty: accept, auth, session, rate-limit, routing
│   ├── server-world/               # ECS server-side: movement, combat, AI, spawn
│   ├── server-database/            # Adapter PostgreSQL / Redis
│   └── server-features/
│       ├── feature-account/
│       ├── feature-character/
│       ├── feature-inventory/
│       ├── feature-shop/
│       ├── feature-dialogue/
│       ├── feature-quest/
│       ├── feature-friend/
│       ├── feature-mail/
│       ├── feature-chat/
│       ├── feature-guild/
│       ├── feature-party/
│       ├── feature-trade/
│       ├── feature-pvp/
│       ├── feature-monster/        # ADR-037 — monster_template + monster_spawn
│       ├── feature-achievement/    # ADR-062 — thành tựu + danh hiệu (bộ đếm mốc, danh hiệu đang đeo)
│       ├── feature-checkin/        # ADR-060 — điểm danh hằng ngày (chu kỳ 7 ngày, quà qua thư hệ thống)
│       ├── feature-market/         # ADR-063 — phường thị (ký quỹ đồ, phí 5%, hết hạn trả thư)
│       └── feature-gm/             # ADR-052 — quyền GM (gm_role) + audit (gm_audit_log) + parser /gm
│
├── quality/
│   └── architecture-tests/         # ArchUnit (ADR-011) — enforce dependency rule
│
├── tools/
│   ├── asset-pipeline/             # CLI export/pack/validate asset (ADR-029)
│   ├── blender/                    # Script Blender của dây chuyền art (KHÔNG phải Gradle module)
│   └── load-test/                  # Client giả lập qua socket thật (BENCHMARK.md §5, Phase 9 lát cắt 2)
│
├── assets/
│   ├── raw/                        # Art nguồn (.blend/PSD) — KHÔNG đóng gói vào game
│   └── exported/                   # Runtime asset — SINH RA từ assets/raw, gitignore (build output)
│
├── deploy/
│   ├── docker/
│   └── k8s/                        # Chỉ tạo khi ADR chốt cần K8s
│
└── docs/
```

Mỗi thư mục cấp 2 trở lên **có trong `settings.gradle.kts`** là một Gradle module
(`:engine:engine-core`, `:server:server-features:feature-inventory`). Ngoại lệ có chủ ý:
`tools/blender/` chứa script Python chạy trong Blender, `third-party/m2/` là repo Maven, `assets/`
và `deploy/` không phải code Java — không cái nào là Gradle module.

### 1.1. Module dự kiến (CHƯA tồn tại)

Không cái nào trong bảng này có trong `settings.gradle.kts`. **Đừng dựng module rỗng cho đủ danh
sách** — chủ dự án đã quyết như vậy 2026-09-06: 5 thư mục code chết làm mọi lần build đắt thêm mà
không phục vụ ai, và hai cái đầu còn đi ngược [ADR-023](adr/ADR-023-Desktop-First-Input.md)
(desktop-first) vừa Accepted.

| Module dự kiến | Điều kiện để tồn tại |
|---|---|
| `client/client-android`, `client/client-ios` | Post-MVP. Cần **ADR riêng** cho sơ đồ điều khiển cảm ứng — ADR-023 chốt mobile KHÔNG nằm trong MVP |
| `server/server-features/feature-auction` | Khi nhà đấu giá được thiết kế (dải opcode 1000–1099 đã chừa sẵn) |
| `server/server-features/feature-ranking` | Khi bảng xếp hạng được thiết kế (dải opcode 1200–1299 đã chừa sẵn) |
| `tools/content-editor` | Phase xa — xem [EDITOR.md](../12-devtools/EDITOR.md) |

Thêm module mới **phải qua ADR** (CLAUDE.md §4) — kể cả module đã nằm trong bảng này.

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
| ~~**ADR-011**~~ | ✅ **Accepted 2026-08-01** — [ADR-011](adr/ADR-011-Enforce-Dependency-Rules.md) | Hết chặn |
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
