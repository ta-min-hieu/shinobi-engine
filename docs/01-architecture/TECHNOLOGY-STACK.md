# TECHNOLOGY-STACK.md

**Công nghệ nào, phiên bản nào, vì sao, và khi nào thay**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 36](../00-governance/KAGE-CONSTITUTION.md#điều-36-decision-making) — thêm dependency cần chủ dự án duyệt |

---

## ⚠️ Cảnh báo về số phiên bản

Các số phiên bản trong tài liệu này là **baseline đề xuất tại thời điểm soạn thảo**, **chưa được verify** bằng cách chạy thật.

**Trước khi ghi vào `libs.versions.toml`, bắt buộc kiểm tra lại phiên bản mới nhất đang được bảo trì.** Không copy số từ tài liệu này vào production mà không kiểm chứng — đây là quy tắc trung thực ở [AI-WORKING-RULES.md §14](../00-governance/AI-WORKING-RULES.md).

---

## 1. Tiêu chí đánh giá mọi dependency

Điều 36. Mọi thư viện muốn thêm vào dự án phải trả lời **đủ 7 câu**:

1. Có đang được bảo trì? (commit gần nhất, tần suất release)
2. Giấy phép sử dụng? (có tương thích mục tiêu Open Source ở Hiến pháp không?)
3. Hiệu năng? (có allocation trong hot path không?)
4. Kích thước? (ảnh hưởng bundle mobile?)
5. Mức độ phổ biến? (tìm được người biết dùng không?)
6. Rủi ro bảo mật? (CVE history)
7. **Khả năng thay thế?** (nếu chết thì thay bằng gì, chi phí bao nhiêu?)

**Không thêm thư viện chỉ vì tiện.**

---

## 2. Ngôn ngữ & Runtime

| Thành phần | Chọn | Vì sao | Nhược điểm | Khi nào thay | ADR |
|---|---|---|---|---|---|
| **Java 25 (LTS)** | LTS mới nhất | Record/Sealed/Pattern Matching giảm boilerplate mạnh; Virtual Thread cho I/O; hiệu năng JIT trưởng thành | Verbose hơn Kotlin; startup chậm hơn Go | Không thay. Chỉ nâng LTS kế tiếp sau khi verify toàn bộ dependency | [ADR-001](adr/ADR-001-Why-Java25.md) |
| **Temurin JDK 25** | Adoptium build | Miễn phí thương mại, không rủi ro license Oracle | — | Nếu Adoptium ngừng build LTS | [ADR-001](adr/ADR-001-Why-Java25.md) |
| Kotlin | **Chỉ** Gradle DSL | Kotlin DSL có type-safety cho build script | — | Không mở rộng sang gameplay — vi phạm "một ngôn ngữ chủ đạo" | — |

**Cấm**: dùng Preview Feature trong Production khi chưa đánh giá kỹ.

---

## 3. Build

| Thành phần | Chọn | Vì sao | Nhược điểm | Khi nào thay |
|---|---|---|---|---|
| **Gradle 8.x** (bản tương thích Java 25) | Chuẩn de-facto cho multi-module Java/libGDX | Build phức tạp, học khó | Không thay — Maven không hợp multi-module + convention plugin ở quy mô này |
| **Kotlin DSL** | Type-safe, IDE autocomplete | Compile chậm hơn Groovy DSL | Không thay |
| **Version Catalog** (`libs.versions.toml`) | Nguồn version **duy nhất**, chống lệch version giữa module | — | Không thay |
| **Convention Plugins** (`build-logic/`) | Không copy-paste config qua 20 module | Thêm 1 lớp gián tiếp | Không thay |

---

## 4. Client

| Thành phần | Version | Vai trò | Vì sao | Nhược điểm | Khi nào thay | ADR |
|---|---|---|---|---|---|---|
| **libGDX** | 1.13.x | Core engine | Java thuần, đa nền tảng thật, cộng đồng lớn, tích hợp HyperLap2D | API low-level hơn Unity; không có editor | Nếu ngừng bảo trì hoặc HyperLap2D bỏ tương thích | [ADR-002](adr/ADR-002-Why-libGDX.md) |
| **LWJGL3** | bundled | Backend Desktop | Chuẩn của libGDX cho desktop | — | Không thay | [ADR-002](adr/ADR-002-Why-libGDX.md) |
| **Ashley ECS** | 1.7.x | ECS | Tích hợp sẵn hệ sinh thái libGDX, đủ dùng MVP | ⚠️ **Bảo trì chậm**; không archetype-based, cache locality kém | **Đánh giá lại cuối Phase 9** — ứng viên số 1 để thay | [ADR-006](adr/ADR-006-Why-ECS.md) |
| **Box2D** | gdx-box2d | Physics 2D | Ổn định, chuẩn ngành | Overkill nếu chỉ cần AABB | Nếu chỉ dùng va chạm đơn giản → cân nhắc tự viết | — |
| **HyperLap2D** | bản tương thích libGDX 1.13.x | Scene editor + runtime | Editor trực quan cho libGDX — thứ libGDX thiếu | Dự án nhỏ, rủi ro bảo trì | Nếu ngừng phát triển → chuyển sang Tiled hoặc editor tự viết | — |
| **Scene2D** | bundled | UI | Đi kèm libGDX, đủ cho HUD/menu | API cũ, layout khó | Không thay ở MVP | — |
| **gdx-freetype** | bundled ext | Font | **Bắt buộc cho tiếng Việt có dấu** | — | Không thay | — |

⚠️ **Box2D chỉ dùng cho va chạm & vật lý môi trường. KHÔNG dùng tính damage/combat** — combat là logic gameplay thuần, không phải physics.

---

## 5. Server

| Thành phần | Version | Vai trò | Vì sao | Nhược điểm | Khi nào thay | ADR |
|---|---|---|---|---|---|---|
| **Netty** | 4.1.x (LTS) | Network I/O | Zero-copy, buffer pooling → ít GC pressure; production-proven | API phức tạp; dễ block EventLoop nếu không hiểu | Nếu Virtual Thread I/O model chứng minh đủ tốt cho game server | [ADR-003](adr/ADR-003-Why-Netty.md) |
| **Protobuf** | 3.25+ / 4.x | Packet serialization | Nhỏ gọn, schema có version, sinh code đa ngôn ngữ | Cần bước codegen; khó debug bằng mắt | Nếu cần human-readable → cân nhắc cho admin API riêng, không cho game packet | — |
| **PostgreSQL** | 16 / 17 | Source of truth | JSONB mạnh, ACID, license permissive | Vận hành phức tạp hơn MySQL | Không thay. Cần scale ghi → sharding, không đổi RDBMS | [ADR-004](adr/ADR-004-Why-PostgreSQL.md) |
| **Redis 8.x** | — | Cache, session, leaderboard, pub/sub | Chuẩn ngành, sorted set hợp leaderboard; **AGPLv3 — OSI-approved từ bản 8 (5/2025)** | Network copyleft nếu phân phối lại bản sửa đổi (không ảnh hưởng self-host nguyên bản) | Không thay | [ADR-005](adr/ADR-005-Why-Redis.md) ✅ |
| **HikariCP** | mới nhất | Connection pool | Nhanh nhất, ít allocation, chuẩn de-facto | — | Không thay | — |
| **Flyway** | — | DB migration | Cần version + rollback (Điều 40); SQL tường minh khớp ADR-004 | Không có rollback tự động ở bản Community | Không thay | [ADR-013](adr/ADR-013-Flyway-vs-Liquibase.md) ✅ |

---

## 6. Infrastructure

| Thành phần | Vai trò | Vì sao | Nhược điểm | Khi nào dùng |
|---|---|---|---|---|
| **Docker** | Container | Môi trường nhất quán dev↔prod | — | Từ Phase 0 |
| **Docker Compose** | Orchestration dev/CI | Đủ cho 1 server + DB + Redis | Không HA | Từ Phase 0 |
| **Kubernetes** | Orchestration production | Tự scale, rolling update, self-heal | ⚠️ **Phức tạp lớn** — over-engineering nếu chỉ 1 server nhỏ | **Chưa chốt** — xem [KUBERNETES.md](../07-infrastructure/KUBERNETES.md) (stub có chủ đích) |
| **GitHub Actions** | CI/CD | Miễn phí cho repo, tích hợp sẵn | Lock-in nhẹ | Từ Phase 0 |

---

## 7. Observability

| Thành phần | Vai trò | Vì sao | Khi nào dùng |
|---|---|---|---|
| **SLF4J + Logback** | Logging facade + impl | Chuẩn de-facto; JSON structured log cho production | Phase 0 |
| **Micrometer** | Metrics facade | Không lock-in backend metrics | Phase 3 |
| **Prometheus** | Metrics storage | Pull model hợp game server | Phase 9 |
| **Grafana** | Dashboard | Chuẩn ngành | Phase 9 |
| **OpenTelemetry** | Tracing | Trace xuyên gateway→world→feature→DB | Phase 9 — khi có nhiều service |

⚠️ Micrometer + OpenTelemetry có vùng chồng lấn (cả hai làm metrics). **Cần quyết định ranh giới** trước khi dùng cả hai — nếu không sẽ có 2 hệ metrics song song. Xem [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md).

---

## 8. Testing & Quality

| Thành phần | Vai trò | Ghi chú |
|---|---|---|
| **JUnit 5** | Unit test | Chuẩn toàn dự án |
| **AssertJ** | Assertion | Đọc dễ hơn `assertEquals` |
| **Mockito** | Mocking | Dùng **có kiểm soát** — ưu tiên Fake ([TESTING.md](../10-quality/TESTING.md)) |
| **Testcontainers** | Integration test | PostgreSQL + Redis thật trong container, không dùng DB của dev |
| **JMH** | Benchmark | Bắt buộc cho hot path (Điều 30) |
| **Spotless** | Format | Auto-format, không tranh luận style |
| **Checkstyle** | Style check | Build fail nếu lỗi |
| **SpotBugs** | Bug pattern | Build fail nếu lỗi nghiêm trọng |
| **PMD** | Code smell | Build fail nếu lỗi nghiêm trọng |
| **Error Prone** | Compile-time bug catch | Bắt lỗi Google đã gặp ở quy mô lớn |
| **SonarQube** | Tổng hợp chất lượng | ⏳ Tuỳ chọn — chỉ khi có hạ tầng host |

---

## 9. Cross-Platform

| Nền tảng | Backend | Ưu tiên | Ghi chú |
|---|---|---|---|
| Windows / Linux / macOS | LWJGL3 | **#1 — mục tiêu MVP** | — |
| Android | libGDX Android backend | #2 | Cần chốt câu hỏi mobile-first hay desktop-first |
| iOS | RoboVM | #3 | ⚠️ Chi phí cao: cần máy macOS để build |
| Web (GWT/TeaVM) | — | Ngoài scope | Cân nhắc lại nếu cần demo trình duyệt |

---

## 10. Công nghệ bị TỪ CHỐI (và vì sao)

Ghi lại để không ai đề xuất lại:

| Công nghệ | Vì sao từ chối | ADR |
|---|---|---|
| **Spring Boot** | Reflection + annotation scan runtime, vi phạm Điều 31; DI Container ẩn side-effect, vi phạm Điều 12 | [ADR-009](adr/ADR-009-Why-No-Spring-Boot.md) |
| **Hibernate / JPA** | Lazy loading + N+1 + proxy động khó kiểm soát ở hot path; ORM che mất SQL thật | Nêu trong [ADR-004](adr/ADR-004-Why-PostgreSQL.md) |
| **MongoDB** cho dữ liệu core | Không ACID mạnh — sai lệch = mất item/gold thật của người chơi | [ADR-004](adr/ADR-004-Why-PostgreSQL.md) |
| **Java Serialization** | Lỗ hổng bảo mật kinh điển, chậm, vi phạm Điều 31 | — |
| **Unity / Godot** | Không phải Java — ngoài ràng buộc ngôn ngữ dự án | [ADR-002](adr/ADR-002-Why-libGDX.md) |
| **Kotlin cho gameplay** | Phá "một ngôn ngữ chủ đạo"; team/AI phải context-switch | — |

---

## 11. Dependency Evaluation Log

Mọi dependency mới **ngoài** danh sách trên phải ghi vào đây (Điều 36):

| Thư viện | Mục đích | Bảo trì? | License | 7 câu §1 đã trả lời? | Đánh giá bởi | Ngày |
|---|---|---|---|---|---|---|
| *(chưa có — điền khi thêm)* | | | | | | |

---

## 12. Quyết định chưa chốt

| # | Vấn đề | Đang chặn gì | ADR |
|---|---|---|---|
| 1 | **Kubernetes hay chỉ Docker Compose** | [KUBERNETES.md](../07-infrastructure/KUBERNETES.md), deploy/ | ❌ Cần ADR-014 |
| 2 | **Ashley vs ECS tự viết** | Không chặn MVP; đánh giá lại Phase 9 | [ADR-006](adr/ADR-006-Why-ECS.md) — Accepted tạm |
| 3 | **Micrometer vs OpenTelemetry** ranh giới | [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) | ❌ Cần ADR-015 |
| 4 | **Lua vs GraalJS** cho scripting | [SCRIPTING.md](../06-plugin/SCRIPTING.md) | ❌ Cần ADR-016 |

**Đã chốt (2026-07-19):** Redis ([ADR-005](adr/ADR-005-Why-Redis.md) ✅), Flyway ([ADR-013](adr/ADR-013-Flyway-vs-Liquibase.md) ✅).

**Không code chạm các thành phần trên khi ADR chưa Accepted** (Điều 37).

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Java 25 LTS + Temurin | Accepted — [ADR-001](adr/ADR-001-Why-Java25.md) |
| 2 | libGDX + LWJGL3 + HyperLap2D | Accepted — [ADR-002](adr/ADR-002-Why-libGDX.md) |
| 3 | Netty + Protobuf | Accepted — [ADR-003](adr/ADR-003-Why-Netty.md) |
| 4 | PostgreSQL, không ORM | Accepted — [ADR-004](adr/ADR-004-Why-PostgreSQL.md) |
| 5 | Ashley ECS tạm thời, bọc sau `engine-ecs` | Accepted có điều kiện — [ADR-006](adr/ADR-006-Why-ECS.md) |
| 6 | Không Spring Boot | Accepted — [ADR-009](adr/ADR-009-Why-No-Spring-Boot.md) |
| 7 | Box2D không dùng cho combat | Accepted |
| 8 | Mọi số version phải verify trước khi vào `libs.versions.toml` | Accepted |

## ADR cần tạo

| ADR | Nội dung | Ưu tiên |
|---|---|---|
| **ADR-016** | Lua vs GraalJS cho scripting | Cao — chặn Phase 2 |
| **ADR-014** | Kubernetes hay Docker Compose | Thấp — chặn Phase 9 |
| **ADR-015** | Micrometer vs OpenTelemetry | Trung bình — chặn Phase 9 |

## Tài liệu liên quan

- [ADR Index](adr/README.md) — chi tiết từng quyết định
- [ARCHITECTURE.md](ARCHITECTURE.md) — công nghệ lấp vào ô nào
- [JAVA-GUIDELINES.md](../02-coding-standards/JAVA-GUIDELINES.md) — dùng Java 25 thế nào
- [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md) — chi tiết §8

## Việc cần làm tiếp theo

1. **Verify toàn bộ số version §2–§8** bằng cách kiểm tra release thật trước khi viết `libs.versions.toml`.
2. Chốt ADR-016 (Lua/GraalJS) — còn chặn sớm nhất (ADR-005 Redis, ADR-013 Flyway đã Accepted 2026-07-19).
3. Phase 0: dựng `libs.versions.toml` từ các version **đã verify**, không từ tài liệu này.
