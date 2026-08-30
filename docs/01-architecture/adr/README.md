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
| [ADR-007](ADR-007-Why-Plugin-System.md) | Xây dựng Plugin System | 🔀 **Superseded by ADR-030** | — |
| [ADR-008](ADR-008-Why-EventDriven.md) | Dùng Event-Driven giữa hệ thống gameplay | ✅ Accepted | — |
| [ADR-009](ADR-009-Why-No-Spring-Boot.md) | Không dùng Spring Boot cho Game Server | ✅ Accepted | — |
| [ADR-010](ADR-010-Realm-Model.md) | Single-realm ở MVP, giữ sẵn `realm_id` | ✅ Accepted | — |
| [ADR-011](ADR-011-Enforce-Dependency-Rules.md) | Dùng ArchUnit thực thi luật dependency bằng máy | ✅ Accepted *(2026-08-01)* | — |
| [ADR-012](ADR-012-Package-Root.md) | Package root `dev.kage` | ✅ Accepted | — |
| [ADR-013](ADR-013-Flyway-vs-Liquibase.md) | Dùng Flyway làm công cụ migration DB | ✅ Accepted | — |
| [ADR-014](ADR-014-Kubernetes-vs-Docker-Compose.md) | Docker Compose + VM đơn, không dùng Kubernetes | ✅ Accepted | — |
| [ADR-015](ADR-015-Micrometer-vs-OpenTelemetry.md) | Micrometer cho Metrics, OpenTelemetry cho Tracing | ✅ Accepted | — |
| [ADR-016](ADR-016-Scripting-Engine.md) | Dùng GraalJS làm scripting engine | ✅ Accepted | — |
| [ADR-017](ADR-017-Auth-Token.md) | Auth token: session token tự phát hành + Redis | ✅ Accepted | — |
| [ADR-018](ADR-018-Tick-Rate.md) | Tick rate **60Hz** | ✅ Accepted | — |
| [ADR-019](ADR-019-GC-Choice.md) | Dùng ZGC cho server production | ✅ Accepted | — |
| [ADR-020](ADR-020-Viewport-Strategy.md) | `FitViewport` cho camera gameplay — công bằng PvP | ✅ Accepted *(2026-08-01, khớp code `WorldScreen` từ Phase 1)* | — |
| [ADR-021](ADR-021-Box2D-vs-AABB.md) | Không dùng Box2D cho di chuyển — AABB tự viết khi có map thật | ✅ Accepted *(2026-08-01, dựa dữ liệu Phase 3-9 đã đóng)* | PHYSICS.md §2-§4/Technical Decision #2/#8 cần rà lại (chưa làm) |
| [ADR-022](ADR-022-Custom-FSM-No-Gdx-Ai.md) | Tự viết FSM cho AI quái, không dùng gdx-ai | ✅ Accepted *(2026-08-01, khớp code `AiSystem` từ Phase 4)* | — |
| [ADR-024](ADR-024-Asset-Distribution-Strategy.md) | Đóng gói toàn bộ asset trong build, hoãn patch/CDN/Play Asset Delivery tới Revisit Trigger | ✅ Accepted *(2026-08-01)* | **Phần "đóng gói toàn bộ" bị [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) amend**; phần hoãn CDN/OBB vẫn giữ |
| [ADR-025](ADR-025-Idempotency.md) | Idempotency cho Command có side-effect | ✅ Accepted | — |
| [ADR-026](ADR-026-Dungeon-Instance-Model.md) | Dungeon instance model: cùng process, World riêng trong 1 JVM | ✅ Accepted | — |
| [ADR-027](ADR-027-TLS-Transport-Security.md) | TLS cho kết nối game: in-process Netty (server) + JDK `SSLSocket` pin cert (client), self-signed cho Closed Beta | ✅ Accepted *(2026-07-31, chủ dự án xác nhận)* | — |
| [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) | Bản cài đóng gói 1 tier đồ họa mặc định (MEDIUM), tier khác tải theo yêu cầu; client luôn qua `ensureAssetsAvailable(tier)` | ✅ Accepted *(2026-08-01)* | **Amend ADR-024**; cần `tools/asset-pipeline` xuất atlas tách tier + manifest theo tier |
| [ADR-029](ADR-029-Asset-Pipeline-Tool.md) | Tạo module `tools:asset-pipeline`, dùng `gdx-tools` TexturePacker đóng gói atlas | ✅ Accepted *(2026-08-01)* | Mở khoá [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md); v1 không gồm upsert DB — chờ `V19__create_art_registry.sql` được duyệt |
| [ADR-030](ADR-030-No-Plugin-System.md) | Không xây Plugin System — xoá `engine-plugin-api` | ✅ Accepted *(2026-08-01)* | Supersedes ADR-007 |
| [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) | Nhân vật render 3D thời gian thực qua camera orthographic khoá góc (2.5D); VFX/icon/UI vẫn 2D | ✅ Accepted *(2026-08-03)* | Amend [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md), thu hẹp phạm vi [ADR-029](ADR-029-Asset-Pipeline-Tool.md); thêm dependency `gdx-gltf` — **đã giải quyết bằng [ADR-032](ADR-032-JitPack-Dependency-Defence.md)** (vendor vào `third-party/m2/`, không qua JitPack) |
| [ADR-032](ADR-032-JitPack-Dependency-Defence.md) | Đóng gói `gdx-gltf` vào `third-party/m2/` làm kho Maven cục bộ, bỏ hẳn `jitpack.io` khỏi build | ✅ **Accepted** *(2026-08-06)* | Gỡ việc treo #2 của [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md); jar đo thật 318 KB, Apache-2.0, phụ thuộc bắc cầu duy nhất là `gdx` vốn đã có |
| [ADR-033](ADR-033-Ninja-School-Style-Movement.md) | Đổi mô hình di chuyển sang trái/phải + nhảy thật kiểu Ninja School Online — bỏ di chuyển lên/xuống, nhảy né được đòn tầm thấp/lên bệ cao | 🟢 **Accepted** *(2026-08-08)* | Đổi protocol (`MoveCommand`) + đổi mô hình vật lý (trục Z thật lần đầu); kế thừa [ADR-021](ADR-021-Box2D-vs-AABB.md) (không Box2D) |
| [ADR-034](ADR-034-Manual-Attack-And-Experience.md) | Đánh thường phải BẤM mới ra đòn (bỏ auto-target cho người chơi, quái vẫn tự đánh) + hệ thống kinh nghiệm/lên cấp đầu tiên | 🟢 **Accepted** *(2026-08-09)* | Chủ dự án quyết sau playtest; thêm opcode 407/208, mâu thuẫn GDD §6 cho tới khi GDD được sửa |
| [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) | Lấp 4 khoảng trống đồng bộ trạng thái: liệt kê túi đồ (507/508), catalog vật phẩm (509/510), số vàng (209), nhật ký nhiệm vụ (610/611) | ✅ **Accepted** *(2026-08-27)* | Mở khoá giao diện túi đồ/trang bị/cửa hàng/nhật ký nhiệm vụ. Chỉ THÊM 7 opcode, không đổi message đang chạy, không đổi schema DB. Catalog theo **A′** (gửi qua mạng + `version` do server phán quyết) |
| [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) | Trang bị phải có chỉ số thật: thêm `bonus_def`/`bonus_res`/`bonus_hp`/`required_level` vào `item_template`, và cường hoá cộng theo phần trăm chỉ số gốc | ✅ **Accepted** *(2026-08-30)* | Đo được 3 lỗ: 6/7 ô trang bị vô nghĩa (chỉ có cột `bonus_atk`), **cường hoá KHÔNG làm gì** (xác minh bằng chơi thật: +0→+7, mất 8.900 vàng, chỉ số không đổi), và điều kiện cấp độ mà GAMEPLAY.md §7 mô tả thì không tồn tại. Chủ dự án duyệt trọn D1-D5 + `maxHp = 100 + bonus_hp`; **đã thi hành cùng ngày** (migration V22, `EnhancementTable.enhancedBonus`, 4 field `7..10` của `ItemCatalogEntry` — `protocolVersion` không tăng) |
| [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) | Quái đọc từ DB: `monster_template` + `monster_spawn`, thay bốn chỗ hardcode (chỉ số, điểm spawn, EXP, và một phần AI) | ✅ **Accepted** *(2026-08-30)*, đã thi hành | Playtest ADR-036 đo được: quái duy nhất có `atk = 8` nên trọn bộ giáp 10.900 vàng chỉ đổi 7→6 sát thương ⇒ **DEF/RES của V22 không có gì đối chứng**. Cả 4 điểm spawn hardcode CÙNG một mã nên overworld chỉ có một loài. Chủ dự án chọn **module `feature-monster`** + duyệt field `model_key`; migration V23, `MonsterExperience` bị xoá, 3 loài quái |

---

## ADR cần tạo (chưa viết)

Tổng hợp từ toàn bộ tài liệu. **Không code chạm các vùng này khi ADR chưa Accepted.**

| ADR | Nội dung | Ưu tiên | Chặn | Nguồn |
|---|---|---|---|---|
| **ADR-023** | Mobile input scheme (joystick ảo / tap-to-move) | 🟡 Trung bình | Phụ thuộc quyết định Android (TECHNOLOGY-STACK.md — chưa chốt mobile-first hay desktop-first) | [CLIENT.md §5](../../04-client/CLIENT.md) |

> ADR-013, ADR-017, ADR-025 đã Accepted (2026-07-19), ADR-016 đã Accepted (2026-07-20), ADR-026 đã Accepted (2026-07-24), ADR-014/ADR-015 đã Accepted (2026-07-26), ADR-019 đã Accepted (2026-07-28), ADR-027 đã Accepted (2026-07-31) — xem bảng "Danh sách ADR" ở trên, không còn nằm ở đây. ADR-011/020/021/022/024/028/029 tất cả **Accepted 2026-08-01** (chủ dự án xác nhận qua chat, dựa dữ liệu code thật đã chạy qua Phase 1-9); ADR-007 **Superseded** cùng ngày bởi ADR-030 mới (không xây Plugin System, xoá `engine-plugin-api`). Chỉ còn **ADR-023** thật sự "chưa viết" — bị chặn bởi câu hỏi Android chưa chốt, không phải bị bỏ quên.

---

## Ba ADR chặn Phase 0 — trạng thái

1. ✅ **ADR-012** — package root `dev.kage`. Đã chốt.
2. ✅ **ADR-011** — enforce dependency rule bằng ArchUnit. **Code đã chạy thật từ Phase 0** (`quality/architecture-tests`, 8 rule); file ADR viết ngày 2026-07-22, Accepted 2026-08-01.
3. ✅ **ADR-010** — single-realm + `realm_id` dự phòng. Đã chốt.

→ Dự án đã đủ điều kiện mở Phase 0 từ lâu; Phase 0-4 đã xong ([ROADMAP.md](../../11-process/ROADMAP.md)). Cả 3 ADR chặn Phase 0 đều đã Accepted.

---

## Tài liệu liên quan

- [KAGE-CONSTITUTION.md](../../00-governance/KAGE-CONSTITUTION.md) — Điều 37 là luật gốc
- [AI-WORKING-RULES.md §7](../../00-governance/AI-WORKING-RULES.md) — quy tắc AI tạo ADR
- [ARCHITECTURE.md](../ARCHITECTURE.md) — phần lớn ADR bắt nguồn từ đây
- [docs/README.md](../../README.md) — tổng hợp toàn bộ quyết định đang treo
