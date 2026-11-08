# ADR-246: Cảnh báo sắp đầy túi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Cảnh báo sắp đầy túi" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: túi còn ≤ 2 ô trống thì nhắc một dòng (kèm gợi ý /tubanrac, bán nhanh đồ rác)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-204](ADR-204-Loot-Filter.md) (`/tubanrac`) · [ADR-170](ADR-170-Sell-Junk.md) · [ADR-232](ADR-232-Bulk-Dismantle.md) (`/phangiai`) |

---

## Decision

**D1 — Đọc gói túi đồ sẵn có.** `BagSpaceWarningCoordinator` nghe cùng `ListInventoryResponse` (508) với túi đồ
(`PanelHub` đưa gói cho mọi luồng): ô trống = sức chứa − số dòng. Còn **≤ 2** ô ⇒ một dòng nhắc kèm gợi ý `/tubanrac`
(tự bán đồ rác) và `/phangiai` (lô 38). Nhắc **một lần** cho tới khi túi rộng ra lại (> 2 ô). Sức chứa chưa biết (0) ⇒
không nhắc. Không opcode.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | ≤ 2 ô trống (từ gói 508) ⇒ một dòng nhắc + gợi ý `/tubanrac`, `/phangiai`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `BagSpaceWarningCoordinator`.
2. ✅ Test: `Lot39bAlertsTest.freeSlotsIgnoreUnknownCapacity`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
