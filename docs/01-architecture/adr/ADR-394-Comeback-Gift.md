# ADR-394: Quà quay lại

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Quà quay lại" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: Vắng ≥ 7 ngày rồi vào lại ⇒ thư quà chào mừng (linh thạch), mỗi lần vắng một quà). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-289](ADR-289-Loyalty-Milestone-Gift.md) · [ADR-192](ADR-192-Last-Online.md) |

---

## Decision

**D1:** vào world, lần rời world trước (ADR-192) cách ít nhất 7 ngày ⇒ thư hệ thống "Chào mừng trở lại" kèm **500 linh thạch**. Chống trùng bằng khoá idempotency của thư (ADR-058) theo (nhân vật, mốc rời world trước): vào world lại bao nhiêu lần trong cùng đợt vắng cũng chỉ một thư; đợt vắng sau có mốc mới nên có quà mới. Nhân vật chưa từng rời world (mới tạo) không có quà.

**D2:** không bảng mới, không protocol mới — thư đi đường thư hệ thống sẵn có.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thư hệ thống 500 linh thạch, khoá `comeback:<id>:<lần rời trước>` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `Lot58Commands.onEnteredWorld` gọi từ `Lot47Commands.onEnteredWorld` (cạnh tin bang lúc vắng).
2. ✅ Test: `Lot58ServerTest.comebackNeedsSevenDaysAway`; kiểm sống (lùi mốc rời world trong DB).
3. ✅ Kiểm sống 2026-11-14: lùi `last_online_at` của ĐạoHữu01 về 8 ngày trước rồi vào world ⇒ thư hệ thống "Chào mừng trở lại" 500 linh thạch tới (đã xoá thư + trả lại mốc sau khi kiểm). **Lần kiểm đầu bắt được lỗi thật**: khoá idempotency phải là UUID (cột `uuid`), chuỗi thô làm hỏng cả lần chọn nhân vật — đã sửa (UUID tên theo nhân vật + mốc) và bọc việc phụ này trong chặn lỗi để không bao giờ làm hỏng vào world nữa.
