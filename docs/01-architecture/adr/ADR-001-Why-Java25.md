# ADR-001: Chọn Java 25 (LTS) làm ngôn ngữ chủ đạo

| | |
|---|---|
| Status | Accepted |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 8](../../00-governance/KAGE-CONSTITUTION.md), [TECHNOLOGY-STACK.md §2](../TECHNOLOGY-STACK.md) |

---

## Context

Dự án đặt mục tiêu tồn tại ≥10 năm với nhiều lập trình viên và nhiều AI cùng tham gia (Điều 8). Cần một ngôn ngữ:

- Có hệ sinh thái game 2D đủ trưởng thành (libGDX).
- Có hiệu năng đủ cho MMORPG server realtime.
- Đủ phổ biến để tuyển người và để AI sinh code đúng.
- Có lộ trình LTS rõ ràng để không mắc kẹt.

Java 25 là bản LTS mới nhất tại thời điểm quyết định.

## Decision

Dùng **Java 25 (LTS)**, build bằng **Temurin/Eclipse Adoptium JDK 25**, làm ngôn ngữ chủ đạo cho toàn bộ client và server.

Kotlin chỉ dùng cho Gradle build script (Kotlin DSL), **không** cho gameplay.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Java 25 LTS** | Record/Sealed/Pattern Matching giảm boilerplate mạnh; Virtual Thread cho I/O; JIT trưởng thành; libGDX là Java-native; LTS support dài | Verbose hơn Kotlin; startup chậm hơn Go/Rust | **Chọn** — cân bằng tốt nhất giữa hệ sinh thái, hiệu năng, và tính sẵn có của nhân lực |
| Java 21 LTS | Ổn định lâu hơn, nhiều dependency đã verify | Thiếu cải tiến của 22–25; sẽ phải nâng cấp sớm hơn | Không chọn — dự án mới, không có lý do bắt đầu ở LTS cũ |
| Kotlin (JVM) | Ít boilerplate, null-safety ở compile time, coroutines | Team/AI phải context-switch; libGDX vẫn là Java API nên lợi ích giảm; thêm 1 lớp compile | Không chọn — lợi ích không đủ bù chi phí "hai ngôn ngữ trong một codebase" |
| C# (Unity/Godot) | Tooling game tốt hơn hẳn, editor trực quan | Rời bỏ hoàn toàn hệ sinh thái Java; mâu thuẫn mục tiêu "Game Engine 2D bằng Java" ở Điều 4 | Không chọn — ngoài phạm vi tầm nhìn dự án |
| Go / Rust cho server | Hiệu năng cao, GC tốt hơn (Go) / không GC (Rust) | Chia dự án thành 2 ngôn ngữ; không chia sẻ được `shared-protocol` model dạng code chung | Không chọn — phá tính đồng nhất, tăng chi phí bảo trì 10 năm |

## Consequences

**Tích cực:**
- `shared-protocol` dùng chung được giữa client và server — cùng ngôn ngữ, cùng model.
- Record + Sealed làm Packet/DTO ngắn gọn và an toàn (xem [CODING-STANDARDS.md](../../02-coding-standards/CODING-STANDARDS.md)).
- Virtual Thread giải quyết blocking I/O (DB call) mà không cần async/callback hell.
- AI sinh Java tốt do lượng dữ liệu huấn luyện lớn.

**Đánh đổi phải chấp nhận:**
- **GC pause là rủi ro thường trực** cho game loop → phải chọn GC và tune ([JVM.md](../../09-performance/JVM.md)), phải giảm allocation (Điều 31–32).
- Verbose hơn Kotlin → bù bằng Record/Pattern Matching, không bù bằng Lombok (thêm dependency + annotation processing).
- Cấm Virtual Thread cho game loop — game loop luôn Platform Thread (Điều 31 tinh thần; chi tiết [JAVA-GUIDELINES.md](../../02-coding-standards/JAVA-GUIDELINES.md)).

**Module bị ảnh hưởng:** Toàn bộ.

## Revisit Trigger

- Khi Java 25 hết LTS support → đánh giá nâng lên LTS kế tiếp (ADR mới, không sửa ADR này).
- Nếu benchmark Phase 9 cho thấy **GC pause là bottleneck không thể khắc phục** bằng tuning + giảm allocation → mở ADR đánh giá lại phần server (không phải client).
- Không revisit vì lý do "ngôn ngữ khác vui hơn".
