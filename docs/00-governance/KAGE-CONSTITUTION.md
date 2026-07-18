# KAGE-CONSTITUTION.md

**Hiến pháp kỹ thuật của Project Kage**

| | |
|---|---|
| Version | 2.0.0 |
| Status | Draft |
| Project | Project Kage |
| Engine | Kage Engine |
| Language | Java 25 |
| License | Proprietary (có thể đổi thành MIT/Apache-2.0 nếu Open Source) |

---

## Tài liệu này là gì — và không là gì

Tài liệu này chứa **NGUYÊN TẮC (luật)**. Ngắn, ổn định, hiếm khi thay đổi.

Tài liệu này **KHÔNG chứa** chi tiết triển khai. Chi tiết nằm ở tài liệu chuyên môn:

| Muốn biết | Đọc |
|---|---|
| Kiến trúc cụ thể, C4, luồng dữ liệu | [ARCHITECTURE.md](../01-architecture/ARCHITECTURE.md) |
| Quy ước code cụ thể | [CODING-STANDARDS.md](../02-coding-standards/CODING-STANDARDS.md) |
| Công nghệ nào, phiên bản nào | [TECHNOLOGY-STACK.md](../01-architecture/TECHNOLOGY-STACK.md) |
| Cấu trúc module | [REPOSITORY-STRUCTURE.md](../01-architecture/REPOSITORY-STRUCTURE.md) |
| Game này là gì | [GAME-DESIGN-DOCUMENT.md](../13-game-design/GAME-DESIGN-DOCUMENT.md) |
| Bản đồ toàn bộ tài liệu | [docs/README.md](../README.md) |

**Quy tắc phân biệt**: nếu một câu có thể sai sau 6 tháng vì công nghệ đổi → nó **không thuộc** Hiến pháp. Nếu nó đúng bất kể công nghệ nào → nó thuộc Hiến pháp.

---

## CHƯƠNG I — HIỆU LỰC

### Điều 1. Mức ưu tiên tối cao

Mọi quyết định về thiết kế, kiến trúc, quy trình, coding style, hiệu năng, bảo mật, kiểm thử, triển khai đều phải tuân thủ tài liệu này.

Nếu bất kỳ tài liệu, con người, hoặc AI nào tạo ra nội dung trái với tài liệu này, **KAGE-CONSTITUTION.md luôn là nguồn chân lý (Single Source of Truth)**.

### Điều 2. Thứ tự thắng khi mâu thuẫn

```
KAGE-CONSTITUTION.md  →  ADR (Accepted)  →  Tài liệu chuyên môn  →  Code comment
```

Phát hiện mâu thuẫn phải **báo cáo**, không tự ý chọn bên.

### Điều 3. Sửa đổi Hiến pháp

Chỉ chủ dự án được sửa tài liệu này. Mọi sửa đổi phải tăng MAJOR/MINOR version và ghi lý do. AI **không được** sửa tài liệu này kể cả khi thấy bất hợp lý — chỉ được đề xuất.

---

## CHƯƠNG II — TẦM NHÌN & SỨ MỆNH

### Điều 4. Vision

Project Kage không đơn thuần là một game. Mục tiêu là xây dựng:

- Một **Game Engine 2D hiện đại** bằng Java.
- Một **MMORPG Framework** có khả năng mở rộng.
- Một **bộ công cụ phát triển game**.
- Một **nền tảng tái sử dụng** để phát triển nhiều game khác nhau.

### Điều 5. Mission

Gameplay đầu tiên lấy cảm hứng từ thể loại 2D anime ninja MMORPG (Ninja School Online và tương tự), nhưng **toàn bộ kiến trúc phải đủ tổng quát** để hỗ trợ nhiều thể loại game 2D trong tương lai.

Cảm hứng ≠ sao chép. Không sao chép IP, tên riêng, cốt truyện, asset của bất kỳ sản phẩm nào.

### Điều 6. Definition of Success

Project Kage thành công khi:

- Có kiến trúc rõ ràng.
- Có thể mở rộng trong nhiều năm.
- Có thể thay thế từng module độc lập.
- Có thể chạy trên nhiều nền tảng.
- Có tài liệu đầy đủ.
- Có quy trình phát triển rõ ràng.
- **AI và lập trình viên đều có thể tham gia mà không làm giảm chất lượng dự án.**

---

## CHƯƠNG III — TRIẾT LÝ

### Điều 7. Long-term First

Không tối ưu cho việc hoàn thành nhanh. Luôn tối ưu cho: bảo trì nhiều năm · dễ mở rộng · dễ kiểm thử · dễ refactor.

Mọi quyết định kiến trúc phải được đánh giá theo **chi phí bảo trì trong ít nhất 5 năm**.

### Điều 8. Production First

Không viết mã kiểu demo. Không viết mã kiểu tutorial. Không viết mã chỉ để chạy được.

Mọi module phải giả định rằng: sẽ tồn tại ít nhất 10 năm · sẽ có nhiều lập trình viên · sẽ có nhiều AI · sẽ có hàng triệu dòng code.

### Điều 9. Simplicity

Đơn giản luôn được ưu tiên. Hai giải pháp hiệu năng tương đương → chọn cái đơn giản hơn.

Không áp dụng Design Pattern nếu không thực sự cần. Không dùng kỹ thuật phức tạp chỉ để thể hiện.

### Điều 10. Business First

**Gameplay quan trọng hơn Framework.** Engine tồn tại để phục vụ gameplay, không phải ngược lại.

Không xây framework phức tạp chỉ để phục vụ chính framework.

### Điều 11. Maintainability

Code được đọc nhiều hơn được viết. Tối ưu cho việc **đọc**, không tối ưu cho việc viết nhanh.

### Điều 12. Explicit over Implicit

Mọi hành vi quan trọng phải rõ ràng. Không side effect khó đoán. Không magic behavior.

### Điều 13. Composition over Inheritance

Ưu tiên Composition. Chỉ dùng Inheritance khi quan hệ "is-a" thực sự tồn tại.

### Điều 14. Data-Oriented Design

Với gameplay và rendering, luôn ưu tiên: Cache Friendly · Sequential Memory · ECS · Batch Processing.

Nếu có thể biểu diễn bằng **dữ liệu** thì không dùng **đa hình**.

### Điều 15. Server Authority

**Server là nguồn dữ liệu duy nhất. Client chỉ hiển thị. Client không bao giờ được phép quyết định gameplay.**

Vi phạm điều này là bug nghiêm trọng, không phải "tối ưu hiệu năng".

---

## CHƯƠNG IV — NGUYÊN TẮC KỸ THUẬT

### Điều 16. Nguyên tắc bắt buộc

Mọi module đều phải tuân thủ: **SOLID · DRY · KISS · YAGNI · Fail Fast · Defensive Programming · Immutable by Default · Composition over Inheritance**.

### Điều 17. Thứ tự ưu tiên khi đánh đổi

```
Correctness → Readability → Maintainability → Testability → Performance → Elegance
```

**Không bao giờ đảo ngược thứ tự này.** Không đánh đổi correctness lấy performance.

### Điều 18. Testability

Nếu code khó test thì **thiết kế đang có vấn đề** — không phải test framework có vấn đề.

### Điều 19. Scalability

Mọi kiến trúc đều phải giả định: số người chơi sẽ tăng · số map sẽ tăng · số NPC sẽ tăng · số vật phẩm sẽ tăng.

Không thiết kế chỉ phục vụ quy mô nhỏ. Nhưng cũng không xây sẵn hạ tầng cho quy mô chưa tồn tại (mâu thuẫn Điều 9 — cân bằng bằng ADR).

---

## CHƯƠNG V — NGUYÊN TẮC KIẾN TRÚC

### Điều 20. Architecture is an Investment

Kiến trúc không tồn tại để "đẹp". Kiến trúc tồn tại để: giảm chi phí bảo trì · giảm bug · tăng khả năng mở rộng · giảm coupling · tăng khả năng thay thế module.

### Điều 21. Modular First

Không được phép tạo một project khổng lồ. Mỗi module phải có: trách nhiệm rõ ràng · dependency rõ ràng · public API rõ ràng.

**Nếu một module không thể tách riêng thì thiết kế đang có vấn đề.**

### Điều 22. Một module — một trách nhiệm

Không module nào được làm nhiều việc.

Không tạo Utility Project chứa mọi thứ. Không tạo Shared Module chứa business logic — Shared chỉ chứa DTO, Protocol, Constant, Common Model.

### Điều 23. Dependency đi một chiều

```
UI → Application → Domain ← Infrastructure
```

Không được dependency ngược. **Module gameplay chỉ phụ thuộc abstraction**, không phụ thuộc framework cụ thể (Netty, PostgreSQL, Redis, Spring, Docker, Kubernetes).

### Điều 24. Package theo Feature, không theo Loại

❌ `service/ controller/ repository/ dto/`
✅ `inventory/ guild/ quest/ skill/ combat/`

### Điều 25. Kiến trúc Hybrid

Không áp dụng duy nhất một mô hình. Mỗi bài toán dùng mô hình phù hợp nhất:

| Mô hình | Áp dụng cho | KHÔNG áp dụng cho |
|---|---|---|
| Clean / Hexagonal | Login, Account, Character, Inventory, Shop, Guild, Mail, Quest, Auction, Friend, Chat, Ranking | Rendering, Physics, ECS Update Loop |
| ECS + Data-Oriented | Gameplay realtime, Rendering | Nghiệp vụ giao dịch (transaction-heavy) |
| Event-Driven | Giao tiếp giữa các hệ thống gameplay | Luồng cần phản hồi đồng bộ tức thì |

**Lý do**: gameplay thời gian thực cần hiệu năng cao hơn abstraction.

### Điều 26. Giao tiếp bằng Event

Các hệ thống gameplay không gọi trực tiếp lẫn nhau. Giao tiếp qua Event.

### Điều 27. Không hardcode nội dung

Quest · NPC · Dialogue · Cutscene · Event · Festival · EXP · Damage · Cooldown · Spawn · Item · Skill · Drop → **Data-Driven / Scripting**, không hardcode.

Script phải có **sandbox**. Không cho phép script truy cập JVM trực tiếp.

### Điều 28. Cross Platform

Gameplay phải chạy giống nhau trên mọi nền tảng. Không viết `if (Windows)` trong gameplay.

Platform-specific code chỉ nằm trong **Launcher** hoặc **Platform Adapter**.

---

## CHƯƠNG VI — NGUYÊN TẮC HIỆU NĂNG

### Điều 29. Performance là yêu cầu bắt buộc

Performance không chỉ là FPS. Bao gồm: CPU · Memory · GC · Disk · Network · Database · Rendering · Packet · AI · Pathfinding.

### Điều 30. Benchmark trước, tối ưu sau

**Không tối ưu mù quáng.** Mọi tối ưu phải có số liệu chứng minh.

Không chấp nhận lập luận "tôi nghĩ nó nhanh hơn".

### Điều 31. Cấm trong game loop

Không tạo object mới nếu tránh được · Không boxing · Không reflection · Không annotation scan runtime · Không dynamic proxy · Không Java Serialization.

### Điều 32. Giảm allocation

Ưu tiên: Object Pool · Primitive Collection · Reuse Buffer · Reuse Packet · Reuse Vector.

---

## CHƯƠNG VII — DEFINITION OF DONE & DEFINITION OF READY

### Điều 33. Definition of Ready (được phép BẮT ĐẦU)

Một công việc chỉ được bắt đầu khi:

- [ ] Yêu cầu rõ ràng, không mơ hồ.
- [ ] Đã xác định module bị ảnh hưởng.
- [ ] Đã xác định có/không thay đổi: API · Database · Packet.
- [ ] Đã xác định có/không cần ADR — nếu cần, **ADR phải Accepted trước**.
- [ ] Không phụ thuộc quyết định đang treo (xem mục "Quyết định chưa chốt" ở [docs/README.md](../README.md)).
- [ ] Có Acceptance Criteria đo được.

**Thiếu bất kỳ mục nào → chưa được bắt đầu.**

### Điều 34. Definition of Done (được phép coi là XONG)

Một chức năng chỉ hoàn thành khi:

- [ ] Compile thành công.
- [ ] Chạy thành công.
- [ ] Có Unit Test (nếu phù hợp).
- [ ] Không phát sinh warning quan trọng.
- [ ] Không vi phạm Coding Standards.
- [ ] Không duplicate.
- [ ] Không tạo allocation không cần thiết.
- [ ] Có logging hợp lý.
- [ ] Có JavaDoc nếu là Public API.
- [ ] Được benchmark nếu thuộc module hiệu năng cao.
- [ ] Tài liệu đã cập nhật nếu thay đổi kiến trúc.
- [ ] Được review theo checklist ([CODE-REVIEW.md](../02-coding-standards/CODE-REVIEW.md)).

**Thiếu bất kỳ mục nào → chưa xong.** Không có "xong 90%".

---

## CHƯƠNG VIII — QUY TRÌNH

### Điều 35. Development Workflow

**Không được viết code ngay.** Workflow bắt buộc:

```
1. Hiểu yêu cầu      →  2. Phân tích        →  3. Thiết kế
→ 4. Đánh giá rủi ro  →  5. Đề xuất          →  6. Được chấp thuận
→ 7. Implementation   →  8. Testing          →  9. Benchmark (nếu cần)
→ 10. Documentation   →  11. Merge
```

### Điều 36. Decision Making

| Loại quyết định | Ai quyết | Cần gì |
|---|---|---|
| Đặt tên biến, tách method, thêm test | AI/Dev tự quyết | — |
| Thêm class/interface trong module có sẵn | AI/Dev tự quyết | Đúng pattern hiện có |
| Đổi public API của module | Chủ dự án | Đề xuất + phân tích tác động |
| Thêm dependency mới | Chủ dự án | Đánh giá theo [TECHNOLOGY-STACK.md](../01-architecture/TECHNOLOGY-STACK.md) |
| Thêm/xoá/đổi tên module | Chủ dự án | **ADR** |
| Đổi protocol / DB schema | Chủ dự án | **ADR** + kế hoạch migration |
| Đổi kiến trúc | Chủ dự án | **ADR** |

### Điều 37. ADR Rules

- Mọi quyết định kiến trúc lớn phải có **Architecture Decision Record**.
- **Không thay đổi kiến trúc lớn nếu chưa có ADR ở trạng thái Accepted.**
- ADR đánh số tuần tự, **không tái sử dụng số** kể cả khi ADR bị Deprecated/Superseded.
- ADR phải có: Context · Decision · Alternatives Considered · Consequences · **Revisit Trigger**.
- ADR không bị xoá. ADR sai → tạo ADR mới `Supersedes ADR-XXX`.

Xem [ADR Index](../01-architecture/adr/README.md).

### Điều 38. Review Rules

Code Review tập trung theo thứ tự: **Correctness → Architecture → Maintainability → Performance → Style**.

Không review chỉ để tranh luận sở thích cá nhân. Ý kiến về style đã được quy định trong [CODING-STANDARDS.md](../02-coding-standards/CODING-STANDARDS.md) → tranh luận style = sửa tài liệu, không tranh luận trong PR.

### Điều 39. Refactoring Rules

Refactor phải:

- **Không thay đổi behavior.**
- Không phá backward compatibility nếu không có kế hoạch migration.
- Có test **trước và sau**.
- Có benchmark nếu ảnh hưởng performance.

Refactor và thêm tính năng **không được nằm trong cùng một Pull Request**.

### Điều 40. Backward Compatibility

- Client cũ phải hoạt động nếu protocol chưa thay đổi.
- Packet mới phải có version.
- Database migration phải có khả năng rollback khi hợp lý.
- Không xoá API công khai ngay lập tức. Quy trình: **Deprecated → Migration → Removal**.

### Điều 41. Automation

Mọi công việc lặp lại phải được tự động hóa: Build · Test · Lint · Format · Docker Build · Release · Deployment · Asset Packaging.

Không phụ thuộc thao tác thủ công nếu có thể tự động hóa.

---

## CHƯƠNG IX — NGUYÊN TẮC BẢO MẬT

### Điều 42. Security là mặc định

**Security không phải tính năng. Security là yêu cầu mặc định.**

Mọi module đều phải xem xét: Authentication · Authorization · Validation · Encryption · Replay Protection · Rate Limiting · Audit Logging.

### Điều 43. Không tin Client

Mọi dữ liệu từ client là **không đáng tin cho tới khi được server validate**. Xem Điều 15.

### Điều 44. Observability

Nếu hệ thống lỗi — **không được đoán, phải quan sát được**.

Bao gồm: Metrics · Logs · Tracing · Health Check · Profiling · Benchmark.

**Không có Observability thì Production không được coi là hoàn chỉnh.**

---

## CHƯƠNG X — QUY TẮC DÀNH CHO AI

> Bản chi tiết: [AI-WORKING-RULES.md](AI-WORKING-RULES.md) · Bản luôn-nạp: [/CLAUDE.md](../../CLAUDE.md)

### Điều 45. Vai trò

AI là **kỹ sư triển khai**, không phải kiến trúc sư trưởng. AI đề xuất — con người quyết định.

### Điều 46. AI không được phép

- Tự ý thay đổi kiến trúc.
- Tự ý đổi package.
- Tự ý đổi protocol.
- Tự ý đổi database schema.
- Tự ý thêm thư viện.
- Tự ý xoá module.
- Tự ý sửa tài liệu Hiến pháp.
- Tự ý quyết định những mục đang được đánh dấu "chưa chốt".

### Điều 47. AI bắt buộc phải

Trước khi viết code:

1. Phân tích yêu cầu.
2. Xác định module bị ảnh hưởng.
3. Phân tích tác động.
4. Đề xuất ít nhất một phương án.
5. Giải thích ưu và nhược điểm.
6. Chờ xác nhận **nếu thay đổi kiến trúc**.
7. Sau đó mới triển khai.

### Điều 48. Khi AI phát hiện thiết kế chưa tốt

AI **không được tự ý sửa**. AI phải: mô tả vấn đề → phân tích nguyên nhân → đề xuất giải pháp → phân tích chi phí refactor → **chờ quyết định**.

### Điều 49. Nguyên tắc hành xử của AI

- Ưu tiên code **rõ ràng** hơn code "thông minh".
- Không tạo abstraction khi chưa có nhu cầu.
- Phân tích trade-off trước khi tối ưu.
- Giữ tính nhất quán với kiến trúc hiện có.
- Khi có nhiều phương án hợp lý: trình bày ngắn gọn ưu/nhược, nêu phương án đề xuất + lý do.
- **Không bịa số liệu benchmark. Không bịa phiên bản thư viện. Không khẳng định điều chưa verify.**

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Hiến pháp chỉ chứa nguyên tắc; chi tiết tách sang tài liệu chuyên môn | Accepted |
| 2 | Kiến trúc Hybrid (Clean + Hexagonal + ECS + DOD + Event-Driven), không chọn một mô hình duy nhất | Accepted — xem [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md), [ADR-008](../01-architecture/adr/ADR-008-Why-EventDriven.md) |
| 3 | Server Authority tuyệt đối | Accepted — không thương lượng |
| 4 | Thêm Definition of Ready bên cạnh Definition of Done | Accepted |
| 5 | Điều 19 (Scalability) và Điều 9 (Simplicity) có mâu thuẫn cố hữu — giải quyết từng ca bằng ADR, không quy định cứng | Accepted |

## ADR cần tạo

Không có ADR nào phát sinh từ chính tài liệu này. Hiến pháp là tiền đề của ADR, không phải hệ quả.

## Tài liệu liên quan

- [AI-WORKING-RULES.md](AI-WORKING-RULES.md) — chi tiết Chương X
- [ARCHITECTURE.md](../01-architecture/ARCHITECTURE.md) — chi tiết Chương V
- [CODING-STANDARDS.md](../02-coding-standards/CODING-STANDARDS.md) — chi tiết quy ước code
- [PERFORMANCE.md](../09-performance/PERFORMANCE.md) — chi tiết Chương VI
- [SECURITY.md](../08-security/SECURITY.md) — chi tiết Chương IX
- [ADR Index](../01-architecture/adr/README.md) — chi tiết Điều 37

## Việc cần làm tiếp theo

1. Chủ dự án review và chuyển Status: Draft → Approved.
2. Chốt các câu hỏi mở ở [docs/README.md](../README.md) trước khi mở Roadmap Phase 0.
3. Sau khi Approved, mọi tài liệu khác phải được rà lại để đảm bảo không mâu thuẫn Điều 1–49.
