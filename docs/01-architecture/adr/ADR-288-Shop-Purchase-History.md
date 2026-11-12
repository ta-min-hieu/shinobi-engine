# ADR-288: Lịch sử mua cửa hàng NPC

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Lịch sử mua cửa hàng NPC" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: /lichsumua: 20 lần mua gần nhất ở cửa hàng NPC). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-233](ADR-233-Enhance-History.md) (cùng khuôn đọc audit) |

---

## Decision

**D1 — Đọc audit có sẵn.** `BuyItemUseCase` đã ghi `SHOP_BUY {itemTemplateId, quantity, goldCost}` vào `audit_log` từ
Phase 5. `PurchaseHistoryPort`/`PurchaseHistoryFacade` (feature-shop) + `PostgresPurchaseHistoryAdapter` đọc 20 dòng mới
nhất. **V172** thêm chỉ mục một phần `audit_log (character_id, id DESC) WHERE action = 'SHOP_BUY'`.

**D2 — Dây:** `GetShopPurchaseHistoryRequest{}` / `GetShopPurchaseHistoryResponse{repeated ShopPurchaseEntry{item_template_id,
quantity, gold_cost, at_epoch_seconds}}` **1042/1043** (dải Shop). Client `/lichsumua` (hoặc `/purchases`), tên vật phẩm
tra từ catalog. Chỉ cửa hàng NPC thường — cửa hàng vinh dự/bang/danh vọng/mua lại có audit riêng, không gộp.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Audit `SHOP_BUY` + V172 chỉ mục; 1042/1043; `/lichsumua` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `PurchaseHistoryPort`/`Facade`, `PostgresPurchaseHistoryAdapter`, `Lot45Commands.purchaseHistory`, `PurchaseHistoryCoordinator`.
2. ✅ Test: `Lot45IntegrationTest.readsShopPurchasesNewestFirst`.
3. ✅ Kiểm sống gameProbe 2026-11-12: `--buyItems=kunai:1` ⇒ BUY_OUTCOME_OK, ngay sau đó `purchases` ⇒ `mua= 1x1=5` (kunai, 1 cái, 5 linh thạch). Đã trả vàng/kunai/audit về như cũ.
