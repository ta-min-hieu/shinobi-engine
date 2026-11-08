# ADR-231: Huỷ vật phẩm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Huỷ vật phẩm" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: vứt bỏ hẳn một món không cần, có hỏi xác nhận). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-159](ADR-159-Item-Lock.md) (khoá vật phẩm) · [ADR-215](ADR-215-Split-Stack.md) (tách chồng) |

---

## Decision

**D1 — Huỷ cả chồng một món.** `DestroyItemCommand{item_id}` / `DestroyItemResponse{outcome}` 560/561 (dải Inventory).
Muốn huỷ một phần thì tách chồng trước (ADR-215). Không trả vàng, không hoàn gì — khác bán (ADR-170) và phân giải (ADR-097).

**D2 — Chặn như mọi đường rời túi.** Dưới khoá túi (`CharacterInventoryLock`) + `FOR UPDATE` dòng: món **khoá**
(ADR-159) ⇒ `LOCKED`; **đang mặc** ⇒ `EQUIPPED`; **đang chào trong giao dịch** ⇒ `IN_TRADE`; không phải của mình ⇒
`NOT_FOUND`. OK ⇒ xoá kèm ngọc khảm, tẩy luyện, trạng thái linh thú của món, ghi audit `ITEM_DESTROYED` (mẫu, số lượng,
cấp cường hoá) để CSKH tra được khi người chơi kêu "mất đồ".

**D3 — Hai bước ở client.** Chọn món → `/huy` (hoặc `/destroy`) chỉ hiện cảnh báo; `/huy xacnhan` trong **10 giây**
với **cùng món đó** mới gửi lệnh. Đổi món đang chọn hay quá giờ ⇒ phải bắt đầu lại. Server không cần biết bước xác nhận
(nó là chống lỡ tay, không phải bảo mật).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 560/561 huỷ cả chồng; chặn khoá/đang mặc/đang giao dịch; audit `ITEM_DESTROYED`; `/huy` + `/huy xacnhan` 10 giây | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): `ItemDestroyPort`/`ItemDestroyFacade`, `PostgresItemDestroyAdapter`, `Lot38Commands.destroyItem`, `ItemDestroyCoordinator`.
2. ✅ Test: `Lot38IntegrationTest.destroysOnlyFreeItems` (NOT_FOUND/LOCKED/EQUIPPED/OK + audit), `Lot38CoordinatorsTest` (lời xác nhận).
3. ✅ Kiểm sống gameProbe 2026-11-08 (ĐạoHữu01): món khoá ⇒ `LOCKED`, kiếm đang mặc ⇒ `EQUIPPED`, id lạ ⇒ `NOT_FOUND`, chồng kunai thử ⇒ `OK` (dòng biến mất + audit `ITEM_DESTROYED`). Bước xác nhận hai lần là client (không có client đồ hoạ khi kiểm sống) — kiểm bằng test đơn vị.
