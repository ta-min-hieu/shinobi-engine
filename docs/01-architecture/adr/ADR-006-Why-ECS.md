# ADR-006: Dùng ECS cho gameplay, khởi đầu bằng Ashley

| | |
|---|---|
| Status | Accepted **có điều kiện** — xem Revisit Trigger |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 14, 25](../../00-governance/KAGE-CONSTITUTION.md), [ECS.md](../../03-engine/ECS.md), [ADR-002](ADR-002-Why-libGDX.md) |

---

## Context

Quyết định này gồm **hai câu hỏi tách biệt**, thường bị gộp làm một:

1. **Có nên dùng ECS cho gameplay không?**
2. **Nếu có, dùng ECS framework nào?**

Bối cảnh: MMORPG cần update hàng nghìn entity 30–60 lần/giây. Mô hình OOP truyền thống (`class Monster extends Entity`) dẫn tới cây kế thừa sâu, cache miss nhiều, và bài toán kinh điển "quái này vừa bay vừa bơi vừa bắn thì kế thừa từ đâu?".

## Decision

1. **Gameplay xây trên ECS** (Điều 14, 25).
2. **Khởi đầu bằng Ashley ECS**, nhưng bọc kín sau module `engine-ecs`.
3. **Ràng buộc bắt buộc**: `client-core`, `server-world`, và mọi gameplay code **không được import `com.badlogic.ashley.*` trực tiếp**.

Điểm 3 là thứ khiến quyết định này **có thể đảo ngược** mà không phải viết lại gameplay.

## Alternatives Considered

### Câu hỏi 1 — ECS hay OOP?

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **ECS** | Composition thay kế thừa (Điều 13); data-oriented → cache friendly (Điều 14); thêm hành vi mới không sửa class cũ; batch update tự nhiên | Đường cong học tập; khó debug hơn (state phân tán qua Component); over-engineering cho game nhỏ | **Chọn** — game này không nhỏ |
| OOP kế thừa | Trực quan, dễ bắt đầu | Cây kế thừa sâu không tránh được ở MMORPG; cache locality kém; vi phạm Điều 13 | Không chọn |

### Câu hỏi 2 — Framework nào?

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Ashley** | Tích hợp sẵn hệ sinh thái libGDX; API đơn giản, dễ cho AI/người mới; đủ nhanh cho MVP | ⚠️ **Bảo trì chậm**; không archetype-based → cache locality kém hơn ECS hiện đại; `Family` scan có chi phí | **Chọn tạm** — tốc độ ra vertical slice quan trọng hơn tối ưu cực hạn lúc này |
| Artemis-odb | Archetype-based, hiệu năng tốt hơn Ashley | Cộng đồng nhỏ; tích hợp libGDX kém trực tiếp hơn | Không chọn ở MVP — ứng viên hàng đầu nếu thay Ashley |
| Dominion / Fleks / ECS Java khác | Một số nhanh hơn đáng kể | Trẻ, rủi ro bảo trì; ít người biết | Không chọn |
| **Tự viết "Kage ECS"** | Tối ưu đúng nhu cầu; kiểm soát tuyệt đối; phù hợp Điều 4 ("xây Game Engine") | Chi phí lớn; rủi ro cao khi **chưa validate gameplay loop**; vi phạm Điều 9 và Điều 10 (engine phục vụ gameplay, không ngược lại) | **Không chọn BÂY GIỜ** — nhưng là đích đến hợp lý sau Phase 9 |

## Consequences

**Tích cực:**
- Có ECS chạy được ngay, không tốn tháng viết framework.
- Nhờ ràng buộc "không import Ashley trực tiếp", thay ECS về sau chỉ đụng `engine-ecs`.

**Đánh đổi phải chấp nhận:**
- **`engine-ecs` là lớp gián tiếp có chi phí thật** — cả về hiệu năng (một tầng gọi) lẫn công sức (phải thiết kế API riêng). Chấp nhận vì đây chính là thứ mua lại quyền thay ECS sau này.
- ⚠️ **Rủi ro lớn nhất**: lớp bọc bị "rò rỉ" — ai đó import Ashley trực tiếp "cho nhanh", và sau 1 năm quyết định này không còn đảo ngược được. → **Phải enforce bằng build tooling** (ADR-011), không bằng nhắc nhở.
- Ashley có thể là bottleneck ở quy mô lớn → chấp nhận và đo ở Phase 9.

**Module bị ảnh hưởng:** `engine-ecs` (bọc), `client-core` + `server-world` (dùng qua wrapper).

## Revisit Trigger

**Bắt buộc đánh giá lại vào cuối [Phase 9 — Optimization](../../11-process/ROADMAP.md)**, hoặc sớm hơn nếu:

- Benchmark cho thấy ECS update > **4ms/tick** với số entity mục tiêu của 1 map đông người (tick 60Hz = ngân sách 16.6ms/tick, ECS không được ăn quá ~25% — xem [ADR-018](ADR-018-Tick-Rate.md)).
- Ashley không có commit/release trong 24 tháng.
- Cần tính năng ECS mà Ashley không hỗ trợ và không thể workaround.

Nếu thay → tạo ADR mới (`Supersedes ADR-006`), ứng viên: Artemis-odb hoặc Kage ECS tự viết. **Không sửa ADR này.**
