# ADR Index — Project Kage

**Architecture Decision Record** — nơi lưu mọi quyết định kiến trúc lớn và **lý do** đằng sau.

| | |
|---|---|
| Luật gốc | [Hiến pháp Điều 37](../../00-governance/KAGE-CONSTITUTION.md#điều-37-adr-rules) |
| Mẫu | [ADR-TEMPLATE.md](ADR-TEMPLATE.md) |

---

## Vì sao ADR tồn tại

Sau 2 năm, không ai nhớ vì sao chọn Netty thay vì Virtual Thread. Một AI mới vào sẽ thấy "Virtual Thread đơn giản hơn" và đề xuất đổi — **rồi lặp lại cuộc thảo luận đã có, hoặc tệ hơn, đổi thật rồi phát hiện lý do cũ vẫn đúng.**

ADR chặn điều đó. ADR không ghi *ta làm gì* (code ghi rồi) — ADR ghi **vì sao ta không làm cách kia**.

---

## Quy tắc

1. **Không thay đổi kiến trúc lớn nếu chưa có ADR ở trạng thái Accepted** (Điều 37).
2. Đánh số **tuần tự**, **không tái sử dụng số** kể cả khi ADR bị Deprecated.
3. ADR **không bị xoá, không bị sửa nội dung quyết định**. Sai → tạo ADR mới `Supersedes ADR-XXX`.
4. Bắt buộc có: Context · Decision · **Alternatives Considered (≥2 phương án thật)** · Consequences · **Revisit Trigger**.
5. AI **không được** tự đặt Status = Accepted ([AI-WORKING-RULES.md §7](../../00-governance/AI-WORKING-RULES.md)).

---

## Danh sách ADR

| ADR | Tiêu đề | Status | Chặn gì |
|---|---|---|---|
| [ADR-001](ADR-001-Why-Java25.md) | Chọn Java 25 (LTS) làm ngôn ngữ chủ đạo | ✅ Accepted | — |
| [ADR-002](ADR-002-Why-libGDX.md) | Chọn libGDX làm nền tảng Client Engine | ✅ Accepted | — |
| [ADR-003](ADR-003-Why-Netty.md) | Chọn Netty làm nền tảng Network I/O | ✅ Accepted | — |
| [ADR-004](ADR-004-Why-PostgreSQL.md) | Chọn PostgreSQL, không dùng ORM | ✅ Accepted | — |
| [ADR-005](ADR-005-Why-Redis.md) | Dùng Redis làm in-memory cache/session store | ✅ Accepted | — |
| [ADR-006](ADR-006-Why-ECS.md) | Dùng ECS, khởi đầu bằng Ashley | ✅ Accepted *(có điều kiện)* | Đánh giá lại cuối Phase 9 |
| [ADR-007](ADR-007-Why-Plugin-System.md) | Xây dựng Plugin System | ⏳ **Proposed** | Sau Phase 6 — MVP (chốt phạm vi) |
| [ADR-008](ADR-008-Why-EventDriven.md) | Dùng Event-Driven giữa hệ thống gameplay | ✅ Accepted | — |
| [ADR-009](ADR-009-Why-No-Spring-Boot.md) | Không dùng Spring Boot cho Game Server | ✅ Accepted | — |
| [ADR-010](ADR-010-Realm-Model.md) | Single-realm ở MVP, giữ sẵn `realm_id` | ✅ Accepted | — |
| [ADR-012](ADR-012-Package-Root.md) | Package root `dev.kage` | ✅ Accepted | — |
| [ADR-013](ADR-013-Flyway-vs-Liquibase.md) | Dùng Flyway làm công cụ migration DB | ✅ Accepted | — |
| [ADR-016](ADR-016-Scripting-Engine.md) | Dùng GraalJS làm scripting engine | ✅ Accepted | — |
| [ADR-017](ADR-017-Auth-Token.md) | Auth token: session token tự phát hành + Redis | ✅ Accepted | — |
| [ADR-018](ADR-018-Tick-Rate.md) | Tick rate **60Hz** | ✅ Accepted | — |
| [ADR-025](ADR-025-Idempotency.md) | Idempotency cho Command có side-effect | ✅ Accepted | — |

---

## ADR cần tạo (chưa viết)

Tổng hợp từ toàn bộ tài liệu. **Không code chạm các vùng này khi ADR chưa Accepted.**

| ADR | Nội dung | Ưu tiên | Chặn | Nguồn |
|---|---|---|---|---|
| **ADR-011** | Cơ chế enforce dependency rule bằng Gradle | 🔴 Cao | **Phase 0** | [ARCHITECTURE.md §8](../ARCHITECTURE.md) |
| **ADR-014** | Kubernetes hay chỉ Docker Compose | 🟢 Thấp | Phase 9 | [KUBERNETES.md](../../07-infrastructure/KUBERNETES.md) |
| **ADR-015** | Micrometer vs OpenTelemetry (ranh giới) | 🟡 Trung bình | Phase 9 | [OBSERVABILITY.md](../../07-infrastructure/OBSERVABILITY.md) |
| **ADR-019** | Chọn GC (G1 vs ZGC vs Shenandoah) | 🟢 Thấp | Phase 9 | [JVM.md](../../09-performance/JVM.md) |
| **ADR-020** | Viewport strategy (ảnh hưởng công bằng PvP) | 🟡 Trung bình | Phase 1 | [RENDERING.md §3](../../03-engine/RENDERING.md) |
| **ADR-021** | Box2D vs AABB tự viết cho chuyển động nhân vật | 🟢 Thấp | Chờ dữ liệu Phase 3 | [PHYSICS.md §10](../../03-engine/PHYSICS.md) |
| **ADR-022** | gdx-ai hay tự viết FSM/pathfinding | 🟡 Trung bình | Phase 2 | [AI.md §5](../../03-engine/AI.md) |
| **ADR-023** | Mobile input scheme (joystick ảo / tap-to-move) | 🟡 Trung bình | Phase 2 nếu mobile-first | [CLIENT.md §5](../../04-client/CLIENT.md) |
| **ADR-024** | Asset distribution: đóng gói / patch / CDN | 🟢 Thấp | Phase 10 | [RESOURCE.md §10](../../04-client/RESOURCE.md) |
| **ADR-026** | Dungeon instance model (cùng process hay riêng) | 🟢 Thấp | Phase 8 | [GAMEPLAY.md §6](../../05-server/GAMEPLAY.md) |

> ADR-013, ADR-017, ADR-025 đã Accepted (2026-07-19), ADR-016 đã Accepted (2026-07-20) — xem bảng "Danh sách ADR" ở trên, không còn nằm ở đây.

---

## Ba ADR chặn Phase 0 — trạng thái

1. ✅ **ADR-012** — package root `dev.kage`. Đã chốt.
2. ⏳ **ADR-011** — enforce dependency rule. **Còn lại — cần chốt/triển khai ngay đầu Phase 0** (đây là công việc kỹ thuật của Phase 0, không phải quyết định của chủ dự án: chọn ArchUnit + cấu hình rule).
3. ✅ **ADR-010** — single-realm + `realm_id` dự phòng. Đã chốt.

→ **Chỉ còn ADR-011**, và nó là việc làm *trong* Phase 0 (viết ArchUnit test) chứ không chặn *khởi động* Phase 0. Dự án đã đủ điều kiện mở Phase 0.

---

## Tài liệu liên quan

- [KAGE-CONSTITUTION.md](../../00-governance/KAGE-CONSTITUTION.md) — Điều 37 là luật gốc
- [AI-WORKING-RULES.md §7](../../00-governance/AI-WORKING-RULES.md) — quy tắc AI tạo ADR
- [ARCHITECTURE.md](../ARCHITECTURE.md) — phần lớn ADR bắt nguồn từ đây
- [docs/README.md](../../README.md) — tổng hợp toàn bộ quyết định đang treo
