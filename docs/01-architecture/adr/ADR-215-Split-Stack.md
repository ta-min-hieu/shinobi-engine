# ADR-215: Tách chồng vật phẩm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Tách chồng vật phẩm" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: chia một chồng trong túi thành hai chồng để giao dịch / gửi kho dễ hơn). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-160](ADR-160-Bag-Sort.md) (thứ tự ô túi) · [ADR-159](ADR-159-Item-Lock.md) (khoá vật phẩm) |

---

## Decision

**D1 — Một transaction**: khoá túi → khoá chồng → kiểm còn ô trống (sức chứa túi, ADR-088) → trừ chồng cũ, chèn chồng
mới **cuối túi** cùng mẫu, cùng cấp cường hoá và cùng cờ khoá → audit `STACK_SPLIT`. Số tách từ 1 tới số đang có − 1.
Chồng đang mặc hoặc đang chào trong giao dịch không tách được. Không migration.

**D2 — Protocol**: `SplitStackCommand{inventory_item_id, amount}` / `SplitStackResponse{outcome}` 558/559.

**D3 — Client**: chọn chồng trong túi rồi `/tach <số>` (hoặc `/split`); xong thì hỏi lại túi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tách sang ô mới cuối túi, giữ cấp cường hoá + cờ khoá; 558/559; `/tach` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): `StackSplitPort`/`Facade`, `PostgresStackSplitAdapter`, `StackSplitCoordinator`.
2. ✅ Test: `Lot36IntegrationTest` (tách đúng số, từ chối số sai / chồng người khác), `StackSplitCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-08: ĐạoHữu01 `split:13/5` (chồng 15 Tiểu Hồi Xuân Đan) ⇒ `OK`, còn 10 ở ô cũ + 5 ở ô
   mới cuối túi; `split:13/99` ⇒ `INVALID_AMOUNT`; kiếm gỗ đang mặc ⇒ `NOT_FOUND`. Đã gộp lại như cũ.
