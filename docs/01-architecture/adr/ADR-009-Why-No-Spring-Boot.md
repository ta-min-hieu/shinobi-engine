# ADR-009: Không dùng Spring Boot cho Game Server

| | |
|---|---|
| Status | Accepted |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 12, 31](../../00-governance/KAGE-CONSTITUTION.md), [ADR-004](ADR-004-Why-PostgreSQL.md) |

---

## Context

Spring Boot là mặc định của phần lớn backend Java — nên việc **không** dùng nó cần được ghi lại rõ ràng, nếu không sẽ có người (hoặc AI) đề xuất lại mỗi 6 tháng.

Game server realtime khác web service ở ba điểm căn bản:

1. **Không phải request-response.** Game loop chạy liên tục 30–60 lần/giây, giữ state trong bộ nhớ.
2. **GC pause là kẻ thù.** Web service tolerate được 100ms pause; game server thì không.
3. **Vòng đời object phải kiểm soát chặt** (Điều 31–32).

## Decision

**Không dùng Spring Boot**, hoặc bất kỳ DI Container runtime dựa trên reflection, cho `server-*`.

Dùng **Constructor Injection thủ công**, wiring tường minh tại một **composition root** duy nhất mỗi module (VD: `ServerBootstrap.java`).

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Manual Constructor Injection** | Không reflection; không magic; nhìn constructor biết toàn bộ phụ thuộc; startup tức thì; dependency graph trace được bằng mắt | Phải tự viết wiring (~vài trăm dòng ở composition root) | **Chọn** |
| Spring Boot | Hệ sinh thái khổng lồ; nhiều người biết; auto-configuration | **Reflection + annotation scan runtime** → vi phạm Điều 31 trực tiếp; DI Container ẩn side-effect → vi phạm Điều 12 (Explicit over Implicit); startup chậm; kéo theo Spring Data/Hibernate (đã từ chối ở [ADR-004](ADR-004-Why-PostgreSQL.md)); proxy động (`@Transactional`) khó kiểm soát ở hot path | Không chọn |
| Dagger (DI compile-time) | Không reflection runtime → an toàn hiệu năng; DI graph vẫn tường minh; lỗi wiring bắt lúc compile | Thêm annotation processing vào build; thêm 1 dependency; đường cong học tập | **Không chọn ở MVP** — nhưng là fallback hợp lý nếu wiring thủ công phình to |
| Guice | DI runtime nhẹ hơn Spring | Vẫn reflection; vẫn magic | Không chọn |
| Micronaut / Quarkus | DI compile-time, startup nhanh | Vẫn là full framework với vòng đời riêng, xung đột với game loop tự quản | Không chọn |

## Consequences

**Tích cực:**
- Startup gần như tức thì → vòng lặp dev nhanh.
- Không reflection trong hot path (Điều 31).
- Nhìn constructor là biết hết phụ thuộc — không cần dò `@Autowired` khắp nơi.
- Không kéo theo cả một hệ sinh thái mà ta chỉ dùng 5%.

**Đánh đổi phải chấp nhận:**
- **Phải viết wiring thủ công.** Mỗi `server-*` module có 1 composition root chịu trách nhiệm khởi tạo và nối dependency bằng tay.
- Mất các tiện ích Spring: `@Transactional`, `@Scheduled`, Actuator health check, Spring Security → **phải tự làm** phần nào thực sự cần:
  - Transaction → quản lý tường minh trong `server-database` ([DATABASE.md](../../05-server/DATABASE.md)).
  - Scheduled task → scheduler riêng của server tick ([SERVER.md](../../05-server/SERVER.md)).
  - Health check / metrics → Micrometer trực tiếp ([OBSERVABILITY.md](../../07-infrastructure/OBSERVABILITY.md)).
- ⚠️ **Rủi ro**: composition root phình thành file 1000 dòng. → Chia theo module, mỗi module tự expose một `create()` factory.

**Phạm vi**: Nếu sau này cần **HTTP admin API** (dashboard GM), **không** vì thế mà kéo Spring MVC vào. Dùng HTTP server nhẹ hoặc build trên Netty sẵn có — quyết định riêng khi có nhu cầu thật.

**Module bị ảnh hưởng:** `server-gateway`, `server-world`, `server-features/*`, `server-database`.

## Revisit Trigger

- Nếu composition root vượt ~500 dòng và gây bug wiring lặp lại (**đo được**, không cảm tính) → mở ADR đánh giá **Dagger** (DI compile-time).
- **Không bao giờ** quay lại Spring Boot cho phần Game Loop / ECS — mâu thuẫn trực tiếp Điều 31, không thương lượng.
- Phần **không** thuộc game loop (VD: một admin service tách riêng hoàn toàn, chạy process riêng) có thể dùng framework khác — cần ADR riêng.
