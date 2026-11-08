# ADR-274: Tổng giá trị túi đồ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Tổng giá trị túi đồ" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: /giatri: cộng giá bán NPC của mọi món trong túi). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-170](ADR-170-Sell-Junk.md) (bán nhanh đồ rác) |

---

## Decision

**D1 — Server tính** (luật vùng client: không tự tính giá). `GetBagValueRequest{}` → `GetBagValueResponse{total_gold,
priced_stacks, unpriced_stacks}` (570/571, dải Inventory). Tổng = Σ `shop_item.sell_price` × số lượng của mọi chồng trong
túi (cộng/nhân bão hoà, không tràn); chồng không có giá thu mua của cửa hàng đếm riêng, không cộng. Chỉ đọc, không DB mới.

**D2 —** Client `/giatri` (hoặc `/bagvalue`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 570/571; Σ sell_price × số lượng, bão hoà | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `Lot43Commands.bagValue`, `BagValueCoordinator`.
2. ✅ Test: `Lot43CommandsTest` (cộng bão hoà).
