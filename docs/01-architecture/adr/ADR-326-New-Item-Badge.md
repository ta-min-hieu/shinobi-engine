# ADR-326: Nhãn "mới" cho đồ vừa nhặt

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Nhãn "mới" cho đồ vừa nhặt" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: vật phẩm vừa vào túi có dấu "mới" cho tới khi mình mở túi xem). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | — |

---

## Decision

**D1 — Thuần client.** Mẫu vật phẩm vừa vào túi (`ItemGrantedEvent`) có tiền tố "(mới) " trong khung túi đồ; mở túi ra
rồi đóng lại ⇒ bỏ hết nhãn. Theo dõi đóng/mở theo mỗi gói đến, không cần nhịp khung riêng, không cấp phát. Nhãn theo MẪU
(mọi chồng cùng mẫu đều mang nhãn).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tiền tố "(mới) " theo mẫu tới khi mở túi | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `NewItemsCoordinator`, `InventoryItemText.useNewMark`, `InventoryCoordinator.useNewMark`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Kiểm sống gameProbe.
