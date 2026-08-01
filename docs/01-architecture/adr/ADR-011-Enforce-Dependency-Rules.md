# ADR-011: Dùng ArchUnit để thực thi luật dependency giữa module bằng máy

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-01, chủ dự án xác nhận qua chat) |
| Date | 2026-07-18 (code) — 2026-07-22 (viết ADR) |
| Deciders | Chủ dự án (qua scaffold Phase 0 — xem "Ghi chú") |
| Related | [ARCHITECTURE.md §8](../ARCHITECTURE.md), [CI-CD.md §4](../../07-infrastructure/CI-CD.md), [REPOSITORY-STRUCTURE.md §4](../REPOSITORY-STRUCTURE.md), Điều 23 |

---

## Context

[ARCHITECTURE.md §8](../ARCHITECTURE.md#8-module-dependencies) đặt ra ranh giới dependency **kiểm chứng được bằng máy** (Điều 23): `engine-*`/`client-core`/`server-world` không được import Netty/JDBC/Redis/Spring; `domain/` không được import `infrastructure/`; chỉ `server-gateway` được import Netty; chỉ `server-database` được import JDBC/Redis client. [ADR-006](ADR-006-Why-ECS.md) (chọn Ashley cho ECS, có điều kiện đảo ngược cuối Phase 9) chỉ thực sự đảo ngược được **nếu không có class nào ngoài `engine-ecs` import Ashley trực tiếp** — một lần vi phạm không bị phát hiện là mất khả năng thay ECS sau này.

Ghi ranh giới trong tài liệu không đủ — con người (và AI) sẽ quên hoặc vô tình vi phạm khi code dưới áp lực deadline/nhiều module cùng lúc. [CI-CD.md §4](../../07-infrastructure/CI-CD.md#4-archunit--gate-quan-trọng-nhất) gọi đây là "gate quan trọng nhất": cần công cụ biến ranh giới kiến trúc từ *văn bản người ta nên đọc* thành *luật máy thi hành, vi phạm = build fail*.

## Decision

Dùng **ArchUnit** (`com.tngtech.archunit:archunit-junit5`) trong module riêng `quality/architecture-tests`, chạy như JUnit 5 test thường (`@AnalyzeClasses`/`@ArchTest`) — vi phạm ranh giới dependency làm test đỏ, chặn merge qua CI (`./gradlew test` gồm cả ArchUnit, [CI-CD.md §5](../../07-infrastructure/CI-CD.md)).

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Code review thủ công** | Không cần công cụ mới, linh hoạt | Con người quên/mỏi mệt/không biết hết ranh giới; dự án 1 người thì **không có ai review chéo** — vi phạm lọt thẳng vào `main` | Không chọn |
| **Checkstyle `ImportControl` module** | Đã có Checkstyle sẵn trong stack (`kage.quality-conventions`), không thêm dependency mới | Cấu hình bằng XML tĩnh, không biểu đạt được rule dạng "domain không phụ thuộc infrastructure" theo package tương đối linh hoạt như ArchUnit; thông báo lỗi kém rõ ràng hơn; không test được các rule ngữ nghĩa phức tạp hơn (VD sau này: "record phải immutable") | Không chọn — quá yếu cho nhu cầu |
| **jQAssistant / Deptrack** | Mạnh hơn, phân tích được cả dependency giữa artifact, không chỉ package | Cần chạy 1 server/DB riêng (Neo4j) — nặng cho dự án cá nhân 1 người, quá tải so với nhu cầu (chỉ cần chặn import sai package) | Không chọn — over-engineering |
| **ArchUnit** | Chạy như unit test JUnit 5 bình thường (không cần hạ tầng thêm); rule viết bằng Java, đọc được, dễ mở rộng; cộng đồng game/enterprise Java dùng rộng rãi để enforce Clean Architecture; thông báo lỗi chỉ đúng class/package vi phạm | Không bắt được vi phạm runtime (VD reflection né qua compile-time check) — chấp nhận được vì Điều 23 chủ yếu là ranh giới **import**, ArchUnit đúng bài toán này | **Chọn** |

## Consequences

**Tích cực:**
- Ranh giới Điều 23 (gameplay không biết Netty/JDBC/Redis/Spring, domain không biết infrastructure) giờ là **build fail**, không phải quy ước im lặng — kể cả khi chỉ có 1 người (hoặc AI) code, không ai "quên" được vì máy nhắc.
- [ADR-006](ADR-006-Why-ECS.md)'s điều kiện đảo ngược ECS (không ai import Ashley trực tiếp ngoài `engine-ecs`) được bảo vệ suốt vòng đời dự án, không chỉ lúc review 1 lần.
- Rule đặt trong `quality/architecture-tests`, tách khỏi module nghiệp vụ — sửa/thêm rule không đụng code gameplay.

**Đánh đổi phải chấp nhận:**
- Thêm ~30s vào thời gian CI mỗi lần chạy `./gradlew test` ([CI-CD.md §3](../../07-infrastructure/CI-CD.md)) — chấp nhận được, đây là gate được đánh giá quan trọng nhất trong toàn bộ pipeline.
- Rule dùng `noClasses().that().resideOutsideOfPackage("..")` — bền với đổi package con NHƯNG nếu package root `dev.kage` đổi (không dự tính, xem [ADR-012](ADR-012-Package-Root.md)) thì mọi rule phải sửa lại.
- Không phát hiện được vi phạm qua reflection/service loader né compile-time import — chấp nhận được vì `/CLAUDE.md §6` đã cấm Reflection trong game loop qua quy tắc riêng, không dựa vào ArchUnit cho việc đó.

**Module bị ảnh hưởng:** `quality/architecture-tests` (module test riêng, đã tồn tại từ Phase 0) — không phải module nghiệp vụ nào.

## Revisit Trigger

- Một rule ArchUnit tạo false-positive chặn nhầm code hợp lệ nhiều lần liên tiếp (VD ≥ 3 lần trong 1 tháng) → xem lại cách viết rule đó (thường do package pattern quá rộng/hẹp), không xoá rule.
- Đổi package root `dev.kage` (cần ADR riêng trước, xem [ADR-012](ADR-012-Package-Root.md)) → phải sửa toàn bộ `@AnalyzeClasses(packages = ...)` và pattern package trong rule.
- Cần enforce loại ranh giới ArchUnit không biểu đạt được (VD ràng buộc runtime/bytecode-level phức tạp hơn import) → đánh giá bổ sung công cụ, không thay thế ArchUnit cho phần import hiện có.

---

## Ghi chú (vì sao Status = Proposed dù code đã chạy thật)

`quality/architecture-tests/src/test/java/dev/kage/architecture/DependencyRuleTest.java` (8 rule: cấm Netty ngoài `server.gateway`, cấm Ashley ngoài `engine.ecs`, cấm JDBC/Redis ngoài `server.database`, cấm `domain` phụ thuộc `infrastructure`, cấm Spring, cấm `shared` phụ thuộc gameplay, cấm `engine.ecs` phụ thuộc `engine.core`) đã tồn tại và chạy xanh trong `./gradlew test` từ Phase 0 — [ARCHITECTURE.md §8](../ARCHITECTURE.md) và [CI-CD.md §4](../../07-infrastructure/CI-CD.md) đều đã trích dẫn "Đây là nội dung của ADR-011" như một sự thật đã rồi. Nhưng file ADR-011 **chưa từng được viết** cho tới hôm nay (2026-07-22) — phát hiện khi rà soát `adr/README.md` thấy ADR-011 vẫn nằm ở mục "ADR cần tạo" dù `docs/README.md`/`CI-CD.md` đều coi nó là Accepted.

AI không được tự đặt Status = Accepted (Điều 37/AI-WORKING-RULES §7) — kể cả khi quyết định rõ ràng đã được thực thi trong code merged nhiều phase trước. File này ghi lại đầy đủ Context/Decision/Alternatives/Consequences/Revisit Trigger để chủ dự án xác nhận; chuyển `Status` sang `✅ Accepted` là việc của chủ dự án, không phải AI tự sửa.
