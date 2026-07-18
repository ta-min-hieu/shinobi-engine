# ADR-008: Dùng Event-Driven cho giao tiếp giữa hệ thống gameplay

| | |
|---|---|
| Status | Accepted |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 26](../../00-governance/KAGE-CONSTITUTION.md), [ARCHITECTURE.md §10](../ARCHITECTURE.md) |

---

## Context

Một sự kiện gameplay thường kéo theo nhiều hệ quả không liên quan nhau. Ví dụ "giết quái":

```
MonsterKilled → cập nhật quest
              → rơi đồ
              → cộng điểm ranking
              → kiểm tra achievement
              → ghi analytics
              → cộng exp cho party
```

Nếu `CombatSystem` gọi trực tiếp 6 hệ thống này:
- `CombatSystem` phụ thuộc 6 module → coupling hình sao.
- Thêm hệ thống thứ 7 → **sửa code chiến đấu** (vi phạm Open/Closed, Điều 16).
- `CombatSystem` không test được nếu không có cả 6 hệ thống kia.

## Decision

Các hệ thống gameplay **không gọi trực tiếp lẫn nhau** cho các quan hệ fan-out. Giao tiếp qua **Event Bus**.

**Nhưng có giới hạn rõ ràng** (xem Consequences) — Event không phải giải pháp cho mọi giao tiếp.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Event Bus (in-process)** | Decoupling thật; thêm listener không sửa nguồn; test từng listener độc lập; hợp Điều 26 | Khó trace luồng; thứ tự listener không hiển nhiên; dễ lạm dụng | **Chọn** — có giới hạn ở Consequences |
| Gọi trực tiếp | Trace dễ, IDE "find usages" ra ngay; đơn giản | Coupling hình sao; thêm hệ thống phải sửa nguồn | **Vẫn dùng** khi cần kết quả trả về ngay (xem Consequences) |
| Message broker (Kafka/RabbitMQ) | Bền, replay được, xuyên process | **Over-engineering nặng** cho in-process; thêm hạ tầng vận hành; latency không chấp nhận được trong game tick | Không chọn — cân nhắc lại **chỉ khi** cần event xuyên nhiều server instance |
| Observer pattern thủ công (mỗi System tự giữ listener list) | Không cần bus | Mỗi System tự implement lại → duplicate; không thống nhất | Không chọn |
| Reactive (RxJava/Reactor) | Toán tử mạnh | Allocation nhiều, vi phạm Điều 31; đường cong học tập dốc | Không chọn |

## Consequences

**Tích cực:**
- Thêm `AchievementSystem` không đụng một dòng nào của `CombatSystem`.
- Mỗi listener test độc lập bằng cách bắn Event giả.

**Đánh đổi phải chấp nhận — và cách kiểm soát:**

⚠️ **Event làm mất khả năng trace.** "Ai xử lý event này?" không trả lời được bằng IDE. Đây là chi phí thật, không phải lý thuyết. Kiểm soát bằng **3 quy tắc bắt buộc**:

| Quy tắc | Nội dung |
|---|---|
| **1. Event chỉ dùng cho fan-out** | 1 nguồn → N đích **không quan tâm lẫn nhau** và **không cần trả lời**. Cần kết quả ngay → **gọi Facade trực tiếp**, không dùng Event. |
| **2. Event là sự thật đã xảy ra** | Tên ở **quá khứ**: `MonsterKilledEvent` ✅. Không dùng Event như lệnh: `KillMonsterEvent` ❌ — đó là Command trá hình. |
| **3. Không Event dây chuyền sâu** | Listener của Event A bắn Event B, listener B bắn Event C... → cấm quá **2 tầng**. Sâu hơn = không ai debug nổi. |

**Kỹ thuật:**
- Event Bus **in-process, đồng bộ, không allocation** — bắn event trong tick không được tạo rác (Điều 31). Dùng object pool cho Event nếu cần.
- **Không** dùng Virtual Thread / async cho event trong game tick — phá tính xác định của tick.
- Event phải là `record` immutable (Điều 43).

**Module bị ảnh hưởng:** `engine-ecs` (bus), `server-world`, `server-features/*`.

## Revisit Trigger

- Nếu cần event **xuyên nhiều world instance** (phụ thuộc ADR-010 multi-realm) → mở ADR đánh giá Redis pub/sub hoặc broker. **Không** thay bus in-process — bổ sung thêm một tầng.
- Nếu profiling cho thấy event dispatch chiếm >5% thời gian tick → xem lại thiết kế bus (không phải bỏ Event).
- Nếu phát hiện event dây chuyền >2 tầng xuất hiện trong code → đó là dấu hiệu lạm dụng, siết lại quy tắc.
