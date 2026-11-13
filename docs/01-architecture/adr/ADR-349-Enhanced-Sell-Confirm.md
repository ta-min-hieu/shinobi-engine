# ADR-349: Xác nhận bán đồ cường hoá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Xác nhận bán đồ cường hoá" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: bán món đã cường hoá phải bấm Bán lần hai trong 3 giây để xác nhận, tránh bán nhầm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-170](ADR-170-Sell-Junk.md) · [ADR-159](ADR-159-Item-Lock.md) |

---

## Decision

**D1 — Luật:** bấm Bán món +1 trở lên ⇒ chỉ hiện lời nhắc "bấm Bán lần nữa trong 3 giây"; bấm lại đúng món đó trong 3 giây mới gửi lệnh bán. Món chưa cường hoá bán ngay như cũ. Bán nhanh đồ rác (ADR-170) có bước xem trước riêng nên không đổi.

**D2 — Thuần client**, server không đổi; tách lớp `SellConfirmGuard` để không đẩy độ phức tạp của `InventoryCoordinator` (đang ở trần PMD).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bấm lần hai trong 3 giây với món +1 trở lên; `SellConfirmGuard`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `SellConfirmGuard`, `InventoryCoordinator.onSellSelected`/`askSellConfirm`.
2. ✅ Test: `Lot52ClientTest.enhancedItemsNeedASecondPress`.
3. ⏳ Thuần client — cần chạy client thật để bấm.
