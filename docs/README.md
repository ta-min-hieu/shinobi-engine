# Project Kage — Documentation

2D anime ninja MMORPG (cùng thể loại Ninja School Online) bằng **Java 25 + libGDX + HyperLap2D** (client) và **Netty + PostgreSQL + Redis** (server). Kiến trúc Hybrid: Clean/Hexagonal cho nghiệp vụ, ECS + Data-Oriented cho gameplay realtime, Event-Driven giữa các hệ thống.

> **Dự án hiện là 100% tài liệu — chưa có dòng code nào.** Bộ docs này là bước "Thiết kế" trong workflow ([Điều 35](00-governance/KAGE-CONSTITUTION.md)). Bước tiếp theo hợp lệ: [Roadmap Phase 0](11-process/ROADMAP.md), và chỉ khi chủ dự án cho phép.

---

## Bắt đầu từ đâu

| Nếu bạn là | Đọc |
|---|---|
| **AI/Claude Code** sắp gen code | [AI-CODEGEN-GUIDE.md](AI-CODEGEN-GUIDE.md) — đọc ít, đúng chỗ, dùng template |
| **AI/Claude Code** tìm luật làm việc | [/CLAUDE.md](../CLAUDE.md) (tự nạp) → [AI-WORKING-RULES.md](00-governance/AI-WORKING-RULES.md) |
| **Người mới** vào dự án | Theo thứ tự nhóm 00→13 bên dưới |
| **Muốn biết game là gì** | [GAME-DESIGN-DOCUMENT.md](13-game-design/GAME-DESIGN-DOCUMENT.md) |
| **Muốn biết vì sao chọn X** | [ADR Index](01-architecture/adr/README.md) |
| **Cần bắt đầu code** | [ROADMAP.md](11-process/ROADMAP.md) — nhưng xem "Quyết định đang treo" trước |

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
| [ADR Index](01-architecture/adr/README.md) | 9 ADR + 10 ADR đang chờ |

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
[SECURITY.md](08-security/SECURITY.md) · [OWASP.md](08-security/OWASP.md) 🚧

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

Đây là danh sách tổng hợp toàn bộ thứ **chưa quyết** (chủ dự án quyết, không phải AI). Sắp theo mức chặn.

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

Toàn bộ 4 ADR chặn Phase 3 đã Accepted — **Phase 3 đủ điều kiện mở**. Công thức damage/derived stats đã chốt (2026-07-19, [GDD §5–6](13-game-design/GAME-DESIGN-DOCUMENT.md#5-nhân-vật--tiến-trình)) — **Phase 4 hết bị chặn về game design**, còn lại ADR-016 (Lua vs GraalJS, chặn nếu Phase 4 chạm script sandbox).

### 🔴 Chặn Phase 4 (script sandbox)

| # | Quyết định | ADR | Nguồn |
|---|---|---|---|
| 1 | **Lua vs GraalJS** (khuyến nghị Lua — GraalJS cần đổi runtime) | ADR-016 | [SCRIPTING.md §3](06-plugin/SCRIPTING.md) |

### 🟡 Chặn Phase sau

| # | Quyết định | ADR |
|---|---|---|
| 7 | **Desktop-first hay mobile-first** (ảnh hưởng input/UI/launcher) | ADR-023 |
| 8 | Viewport strategy — công bằng PvP (chặn Phase 1) | ADR-020 |
| 9 | gdx-ai hay tự viết FSM | ADR-022 |
| 10 | Micrometer vs OpenTelemetry ranh giới | ADR-015 |
| 11 | Kubernetes hay Docker Compose | ADR-014 |
| 12 | Chọn GC (ZGC khuyến nghị) | ADR-019 |
| 13 | Phạm vi Plugin (hoặc bỏ hẳn) | ADR-007 |
| 14 | Dungeon instance model | ADR-026 |
| 15 | Asset distribution / CDN | ADR-024 |
| 16 | i18n: có tiếng Anh ở MVP không | — |
| 17 | Mô hình kinh doanh | — |

---

## Trạng thái

Tất cả tài liệu ở `Status: Draft`. Chuyển `Approved` chỉ khi chủ dự án xác nhận — AI không tự xác nhận thay chủ dự án ([Điều 45](00-governance/KAGE-CONSTITUTION.md)).

**Việc cần làm tiếp theo của cả bộ docs:**
1. ✅ 3 quyết định chặn Phase 0 đã chốt (ADR-010/012/018, 2026-07-18). **Sẵn sàng mở [Roadmap Phase 0](11-process/ROADMAP.md).**
2. Đầu Phase 0: triển khai ADR-011 (ArchUnit) như hạng mục kỹ thuật.
3. Chốt dần các quyết định "🔴 Chặn Phase 2–4" theo tiến độ.
