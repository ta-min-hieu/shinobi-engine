# Project Kage — Documentation

2D anime ninja MMORPG (cùng thể loại Ninja School Online) bằng **Java 25 + libGDX + HyperLap2D** (client) và **Netty + PostgreSQL + Redis** (server). Kiến trúc Hybrid: Clean/Hexagonal cho nghiệp vụ, ECS + Data-Oriented cho gameplay realtime, Event-Driven giữa các hệ thống.

> **Cập nhật 2026-07-22**: Phase 0–4 đã code xong, đạt Exit Criteria ([ROADMAP.md](11-process/ROADMAP.md)) — foundation, engine core, network, character/map/movement, combat/skill/monster (kể cả script sandbox GraalJS thật, [ADR-016](01-architecture/adr/ADR-016-Scripting-Engine.md)). Đang ở **Phase 5 — Inventory · Equipment · Shop**. Bộ docs này vẫn là nguồn thiết kế gốc ([Điều 35](00-governance/KAGE-CONSTITUTION.md)) — code phải khớp docs, lệch thì báo cáo mâu thuẫn (§14 [/CLAUDE.md](../CLAUDE.md)), không tự chọn bên nào.

---

## Bắt đầu từ đâu

| Nếu bạn là | Đọc |
|---|---|
| **AI/Claude Code** sắp gen code | [AI-CODEGEN-GUIDE.md](AI-CODEGEN-GUIDE.md) — đọc ít, đúng chỗ, dùng template |
| **AI/Claude Code** tìm luật làm việc | [/CLAUDE.md](../CLAUDE.md) (tự nạp) → [AI-WORKING-RULES.md](00-governance/AI-WORKING-RULES.md) |
| **Người mới** vào dự án | Theo thứ tự nhóm 00→13 bên dưới |
| **Muốn biết game là gì** | [GAME-DESIGN-DOCUMENT.md](13-game-design/GAME-DESIGN-DOCUMENT.md) |
| **Muốn biết vì sao chọn X** | [ADR Index](01-architecture/adr/README.md) |
| **Cần bắt đầu code** | [ROADMAP.md](11-process/ROADMAP.md) — nhưng xem "🔴 ĐANG CHỜ CHỦ DỰ ÁN" trước |

---

## Bản đồ tài liệu

### 00 · Governance — luật tối cao
| Tài liệu | Nội dung |
|---|---|
| [KAGE-CONSTITUTION.md](00-governance/KAGE-CONSTITUTION.md) | **Hiến pháp** — 49 Điều, nguyên tắc bất biến. Ưu tiên cao nhất |
| [AI-WORKING-RULES.md](00-governance/AI-WORKING-RULES.md) | AI làm việc thế nào; chống trôi kiến trúc |

### 01 · Architecture
| Tài liệu | Nội dung |
|---|---|
| [ARCHITECTURE.md](01-architecture/ARCHITECTURE.md) | ⭐ Quan trọng nhất — C4, layer, luồng, scale |
| [REPOSITORY-STRUCTURE.md](01-architecture/REPOSITORY-STRUCTURE.md) | Module Gradle, dependency rule |
| [TECHNOLOGY-STACK.md](01-architecture/TECHNOLOGY-STACK.md) | Công nghệ nào, version, vì sao, khi nào thay |
| [ADR Index](01-architecture/adr/README.md) | 35 file ADR — **33 Accepted · 1 Superseded · 1 Proposed** (đếm 2026-08-29 bằng dòng `Status` từng file); ADR-023 chưa viết |

### 02 · Coding Standards
[CODING-STANDARDS.md](02-coding-standards/CODING-STANDARDS.md) · [JAVA-GUIDELINES.md](02-coding-standards/JAVA-GUIDELINES.md) · [CODE-REVIEW.md](02-coding-standards/CODE-REVIEW.md)

### 03 · Engine
[ENGINE.md](03-engine/ENGINE.md) · [ECS.md](03-engine/ECS.md) · [RENDERING.md](03-engine/RENDERING.md) · [PHYSICS.md](03-engine/PHYSICS.md) · [AI.md](03-engine/AI.md)

### 04 · Client
[CLIENT.md](04-client/CLIENT.md) · [UI.md](04-client/UI.md) · [RESOURCE.md](04-client/RESOURCE.md)

### 05 · Server
[SERVER.md](05-server/SERVER.md) · [NETWORK.md](05-server/NETWORK.md) · [DATABASE.md](05-server/DATABASE.md) · [GAMEPLAY.md](05-server/GAMEPLAY.md)

### 06 · Plugin
[PLUGIN.md](06-plugin/PLUGIN.md) · [SCRIPTING.md](06-plugin/SCRIPTING.md)

### 07 · Infrastructure
[DOCKER.md](07-infrastructure/DOCKER.md) · [CI-CD.md](07-infrastructure/CI-CD.md) · [OBSERVABILITY.md](07-infrastructure/OBSERVABILITY.md) · [KUBERNETES.md](07-infrastructure/KUBERNETES.md) 🚧

### 08 · Security
[SECURITY.md](08-security/SECURITY.md) · [OWASP.md](08-security/OWASP.md)

### 09 · Performance
[PERFORMANCE.md](09-performance/PERFORMANCE.md) · [JVM.md](09-performance/JVM.md) · [BENCHMARK.md](09-performance/BENCHMARK.md)

### 10 · Quality
[TESTING.md](10-quality/TESTING.md) · [STATIC-ANALYSIS.md](10-quality/STATIC-ANALYSIS.md)

### 11 · Process
[GIT-WORKFLOW.md](11-process/GIT-WORKFLOW.md) · [DOCUMENTATION.md](11-process/DOCUMENTATION.md) · [ROADMAP.md](11-process/ROADMAP.md)

### 12 · DevTools 🚧
[EDITOR.md](12-devtools/EDITOR.md) · [ADMIN.md](12-devtools/ADMIN.md) · [LAUNCHER.md](12-devtools/LAUNCHER.md) — *stub có chủ đích*

### 13 · Game Design
[GAME-DESIGN-DOCUMENT.md](13-game-design/GAME-DESIGN-DOCUMENT.md)

### 14 · Art
[ART-PIPELINE.md](14-art/ART-PIPELINE.md) — Blender → **model glTF cho nhân vật (2.5D, [ADR-031](01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md))** + pre-render sprite cho VFX/icon, art direction, 3 mức cấu hình đồ họa client · [BLENDER-SESSION-GUIDE.md](14-art/BLENDER-SESSION-GUIDE.md) — lệnh từng bước, checklist nghiệm thu, bảng lỗi · [ART-QUALITY-STANDARD.md](14-art/ART-QUALITY-STANDARD.md) — thước đo ĐẠT/KHÔNG ĐẠT (benchmark Ninja School Online)

### Xuyên suốt
[GLOSSARY.md](GLOSSARY.md) — thuật ngữ Việt–Anh

### Lớp tối ưu cho AI gen code
| Tài liệu | Vai trò |
|---|---|
| [AI-CODEGEN-GUIDE.md](AI-CODEGEN-GUIDE.md) | Mô hình 3 lớp context, bản đồ task→doc, kỷ luật token |
| [templates/](templates/) | Skeleton `.java` copy-paste: feature-module, ecs, protocol |
| `shared/` · `engine/` · `client/` · `server/` **CLAUDE.md** | Thẻ luật vùng — Claude Code auto-load theo thư mục đang sửa (đặt ở thư mục module thật, kích hoạt từ Phase 0) |

🚧 = **stub có chủ đích** — chưa viết chi tiết vì phụ thuộc quyết định chưa có / Phase xa. Không phải thiếu sót ([DOCUMENTATION.md §3](11-process/DOCUMENTATION.md)).

---

## Nguyên tắc toàn cục

- **Hiến pháp có ưu tiên cao nhất.** Tài liệu khác mâu thuẫn → Hiến pháp thắng ([Điều 2](00-governance/KAGE-CONSTITUTION.md)).
- **Không lặp lại Hiến pháp** — tài liệu chuyên môn trích dẫn link, không copy.
- **Đổi kiến trúc bắt buộc ADR trước** ([ADR Index](01-architecture/adr/README.md)).
- **PR đổi kiến trúc không merge nếu chưa cập nhật tài liệu** (Điều 40).
- Mỗi tài liệu chuyên môn kết thúc bằng 4 mục: Technical Decisions · ADR cần tạo · Tài liệu liên quan · Việc cần làm tiếp theo.

---

## ⚠️ Quyết định đang treo — chốt trước khi code

Đây là danh sách tổng hợp toàn bộ thứ **chưa quyết** (chủ dự án quyết, không phải AI).

## 🔴 ĐANG CHỜ CHỦ DỰ ÁN — cập nhật 2026-08-30

| # | Việc | Đang chặn gì | Đọc ở đâu |
|---|---|---|---|
| ~~1~~ | ✅ **ADR-032 Accepted 2026-08-06 và đã thi hành cùng ngày** — `gdx-gltf` 2.3.0 nằm trong `third-party/m2/`, đã vào `libs.versions.toml` và `client-core`, resolve + compile được với `--offline` (tức lấy thật từ repo, không qua mạng). `WorldScreen` **đã nối** sang nhánh 3D (`df5ace2`) và `RENDERING.md` **đã viết lại** cho hai nhánh render (2026-08-07). Còn lại: chạy pipeline art sinh `.glb` để **nhìn thấy** nó chạy → đo frame time → viết GLSL cel | Không còn chặn | [ADR-032](01-architecture/adr/ADR-032-JitPack-Dependency-Defence.md) · [third-party/m2](../third-party/m2/README.md) |
| ~~2~~ | ✅ **V20 duyệt + thi hành 2026-08-07, bước kiểm mồ côi thi hành 2026-08-08 — ĐÓNG HOÀN TOÀN** — `V20__extend_art_asset_for_3d.sql` thêm `asset_kind`/`model_name`/`tri_counts` + `CHECK art_asset_kind_branch`; `ArtRegistryUpserter` upsert được nhánh `MODEL` (verify thật trên Postgres 17: 15 dòng / 45 file `.glb`, chạy 2 lần bất biến). `OrphanAssetChecker` soi **cả hai** thư mục `assets/exported/atlases/` và `assets/exported/models/`, fail build nếu đĩa có file mồ côi; dòng DB không còn file trên đĩa (stale) chỉ cảnh báo — verify thật qua Postgres 17 (Testcontainers) | Không còn chặn | [ART-PIPELINE §8.6](14-art/ART-PIPELINE.md) |
| 3 | **Desktop-first hay mobile-first** (câu hỏi cũ, chưa trả lời) | ADR-023 (mobile input) chưa viết được; ngân sách tam giác hiện chỉ đo cho desktop — có Android thì phải đo lại trên máy tầm thấp | [CLIENT.md §5](04-client/CLIENT.md) |
| 4 | Font **Be Vietnam Pro** (OFL 1.1) — chỉ còn xác nhận hình thức, license đã kiểm tại nguồn + test glyph PASS | Không chặn gì | [RESOURCE.md §7](04-client/RESOURCE.md) |
| 5 | i18n: có tiếng Anh ở MVP không · Mô hình kinh doanh | Chưa chặn phase nào | [GDD](13-game-design/GAME-DESIGN-DOCUMENT.md) |
| 6 | **GM/admin tool**: in-game command hay web app? mấy cấp quyền? tách service hay nhúng? | Chưa chặn — Closed Beta người quen vận hành bằng SQL tay được. Trả lời thì mới viết được [ADMIN.md](12-devtools/ADMIN.md) | [ADMIN.md](12-devtools/ADMIN.md) |
| ~~7~~ | ✅ **ADR-036 Accepted 2026-08-30 và đã thi hành cùng ngày** — chủ dự án duyệt trọn D1-D5 và chấp nhận `maxHp = 100 + bonus_hp`. Migration **V22** thêm `bonus_def`/`bonus_res`/`bonus_hp`/`required_level` + cân bằng lại 16 món; `EnhancementTable.enhancedBonus` khiến cường hoá **cộng chỉ số thật** (×1,10 mỗi cấp, áp riêng từng món); `EquipItemUseCase` kiểm cấp (`error.equip_level_too_low`); 4 field mới `7..10` của `ItemCatalogEntry` (additive, `protocolVersion` KHÔNG tăng); client hiện "Chỉ số gốc"/"Cần Cấp N" và lấy máu tối đa **của server**. Vòng lặp cốt lõi đã KHÉP KÍN | Không còn chặn | [ADR-036](01-architecture/adr/ADR-036-Equipment-Stats-And-Enhancement-Effect.md) · [GAMEPLAY §7.0/§7.1](05-server/GAMEPLAY.md) |
| **8** | 🆕 **Đổi hệ phái xong, người ĐANG nhìn thấy vẫn thấy model cũ** — đã sửa **một nửa** 2026-08-29 (`ConnectedCharacterRegistry.updateAppearance` khiến mọi `CharacterSpawnedEvent` phát về **sau** mang hệ phái mới; trước đó sai cho tới khi đăng nhập lại). Nửa còn lại **client cũng chặn**: `RemoteCharacterPresenter.onSpawned` **bỏ qua** spawn cho nhân vật đã có mặt (cố ý, tránh entity trùng), nên server có phát lại cũng vô ích. Ba lựa chọn, **cả ba đều là quyết định chứ không phải vá**: (1) server gửi **despawn + spawn** cho mọi observer qua `AreaOfInterestTracker.observersOf` — không đổi protocol nhưng nhân vật **"nháy" một khung hình**; (2) client **cập nhật model khi nhận spawn lặp** — không đổi protocol nhưng **phá thế bất biến "spawn = tạo mới"**; (3) thêm **event "ngoại hình vừa đổi"** — sạch nhất nhưng **đổi protocol** (§4) | Chỉ là lỗi hiển thị, không chặn phase | [ROADMAP mục 29](11-process/ROADMAP.md) |

> ⚠️ **Không còn ADR nào `Proposed`** kể từ 2026-08-30 (ADR-036 được Accept). Câu này đã sai một
> lần trong khoảng 2026-08-29→30 khi ADR-036 còn treo — nên vẫn đúng cái luật ở dưới, đừng tin chữ
> ở đây, kiểm `Status` của từng file ADR.
> Đếm chuẩn 2026-08-30: **34 Accepted · 1 Superseded (ADR-007) · 0 Proposed** trên tổng 35
> file; chỉ **ADR-023** là chưa viết. Luật vẫn đúng nguyên: nếu tài liệu nào còn viết "chờ chủ dự án
> Accept" cho một ADR thì **kiểm dòng `Status` của chính file ADR đó**, đừng tin chữ ở nơi khác —
> chính cái bẫy đó đã bắt được lỗi sổ sách ADR-032 ngày 2026-08-25.

---

## ✅ Đã chốt — lịch sử

### ✅ Đã chốt (2026-07-18) — không còn chặn Phase 0

| Quyết định | ADR |
|---|---|
| Package root `dev.kage` | ✅ [ADR-012](01-architecture/adr/ADR-012-Package-Root.md) |
| Single-realm + `realm_id` dự phòng | ✅ [ADR-010](01-architecture/adr/ADR-010-Realm-Model.md) |
| Tick rate **60Hz** | ✅ [ADR-018](01-architecture/adr/ADR-018-Tick-Rate.md) |

Chỉ còn **ADR-011** (ArchUnit enforce) — là việc kỹ thuật *trong* Phase 0, không chặn khởi động. **→ Dự án đủ điều kiện mở Phase 0.**

### ✅ Đã chốt (2026-07-19) — không còn chặn Phase 3

| Quyết định | ADR |
|---|---|
| **Redis** (Redis 8 quay lại AGPLv3, OSI-approved từ 5/2025) | ✅ [ADR-005](01-architecture/adr/ADR-005-Why-Redis.md) |
| **Flyway** (SQL tường minh, khớp ADR-004) | ✅ [ADR-013](01-architecture/adr/ADR-013-Flyway-vs-Liquibase.md) |
| **Auth token**: session token tự phát hành + Redis (không JWT) | ✅ [ADR-017](01-architecture/adr/ADR-017-Auth-Token.md) |
| **Idempotency** cho Command có side-effect (`idempotencyKey` + enforce DB) | ✅ [ADR-025](01-architecture/adr/ADR-025-Idempotency.md) |

Toàn bộ 4 ADR chặn Phase 3 đã Accepted — **Phase 3 đủ điều kiện mở**. Công thức damage/derived stats đã chốt (2026-07-19, [GDD §5–6](13-game-design/GAME-DESIGN-DOCUMENT.md#5-nhân-vật--tiến-trình)) — **Phase 4 hết bị chặn về game design**.

### ✅ Đã chốt (2026-07-20) — không còn chặn Phase 4 (script sandbox)

| Quyết định | ADR |
|---|---|
| **GraalJS** làm scripting engine (chạy trên Temurin JDK 25 hiện có, không đổi ADR-001) | ✅ [ADR-016](01-architecture/adr/ADR-016-Scripting-Engine.md) |

### ✅ Đã chốt (2026-07-24) — không còn chặn Phase 8

| Quyết định | ADR |
|---|---|
| **Dungeon instance model**: cùng process, World riêng trong 1 JVM (không phải OS process riêng) | ✅ [ADR-026](01-architecture/adr/ADR-026-Dungeon-Instance-Model.md) |

### ✅ Đã chốt (2026-07-26) — không còn chặn Phase 9

| Quyết định | ADR |
|---|---|
| **Micrometer cho Metrics, OpenTelemetry cho Tracing** | ✅ [ADR-015](01-architecture/adr/ADR-015-Micrometer-vs-OpenTelemetry.md) |
| **Docker Compose + VM đơn, không dùng Kubernetes** | ✅ [ADR-014](01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md) |

### ✅ Đã chốt (2026-07-28) — không còn chặn Phase 9

| Quyết định | ADR |
|---|---|
| **ZGC cho server** — soak test 2h14 thật: pause max 0.052ms, không leak | ✅ [ADR-019](01-architecture/adr/ADR-019-GC-Choice.md) |

### ✅ Đã chốt (2026-07-31)

| Quyết định | ADR |
|---|---|
| **TLS**: in-process Netty (server) + JDK `SSLSocket` pin cert (client), self-signed cho Closed Beta | ✅ [ADR-027](01-architecture/adr/ADR-027-TLS-Transport-Security.md) |

### ✅ Đã chốt (2026-08-01)

| Quyết định | ADR |
|---|---|
| **ArchUnit** thực thi luật dependency (đã chạy thật từ Phase 0) | ✅ [ADR-011](01-architecture/adr/ADR-011-Enforce-Dependency-Rules.md) |
| **`FitViewport`** cho camera gameplay — công bằng PvP | ✅ [ADR-020](01-architecture/adr/ADR-020-Viewport-Strategy.md) |
| **Không dùng Box2D** — AABB tự viết cho va chạm địa hình | ✅ [ADR-021](01-architecture/adr/ADR-021-Box2D-vs-AABB.md) |
| **Tự viết FSM** cho AI quái, không dùng gdx-ai | ✅ [ADR-022](01-architecture/adr/ADR-022-Custom-FSM-No-Gdx-Ai.md) |
| **Không xây Plugin System** — xoá `engine-plugin-api` | ✅ [ADR-030](01-architecture/adr/ADR-030-No-Plugin-System.md) *(supersedes ADR-007)* |
| **Asset distribution**: không CDN/patch/OBB cho Closed Beta Desktop | ✅ [ADR-024](01-architecture/adr/ADR-024-Asset-Distribution-Strategy.md) |
| **Graphics tier delivery**: đóng gói 1 tier (MEDIUM), tier khác tải theo yêu cầu | ✅ [ADR-028](01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md) |
| **`tools:asset-pipeline`** + `gdx-tools` TexturePacker | ✅ [ADR-029](01-architecture/adr/ADR-029-Asset-Pipeline-Tool.md) |

### ✅ Đã chốt (2026-08-03)

| Quyết định | ADR |
|---|---|
| **Nhân vật/quái/NPC render 3D thời gian thực** qua camera ortho khoá góc (2.5D); VFX/icon/UI vẫn sprite 2D | ✅ [ADR-031](01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) |

---

## Trạng thái

Tất cả tài liệu ở `Status: Draft`. Chuyển `Approved` chỉ khi chủ dự án xác nhận — AI không tự xác nhận thay chủ dự án ([Điều 45](00-governance/KAGE-CONSTITUTION.md)).

**Việc cần làm tiếp theo của cả bộ docs:**
1. ✅ Phase 0–9 đã code xong + đạt Exit Criteria (checklist thật ở [ROADMAP.md](11-process/ROADMAP.md)) — dự án đang ở **Phase 10 Release**.
2. ✅ [ADR-011](01-architecture/adr/ADR-011-Enforce-Dependency-Rules.md) (ArchUnit) Accepted (2026-08-01).
3. Trả lời 5 dòng ở "🔴 ĐANG CHỜ CHỦ DỰ ÁN" (§ trên) — dòng 1 và 2 đang chặn việc thật, dòng 3 là câu hỏi chiến lược để lâu.
