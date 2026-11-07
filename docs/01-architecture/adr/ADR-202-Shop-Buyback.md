# ADR-202: Mua lại đồ đã bán

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Mua lại đồ đã bán" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: cửa hàng NPC giữ 10 món bán gần nhất, mua lại đúng giá đã bán trong 1 giờ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-025](ADR-025-Idempotency.md) (bán hàng idempotent) · [ADR-170](ADR-170-Sell-Junk.md) (bán nhanh đồ rác) · [ADR-083](ADR-083-Mail-Enhanced-Items.md) (món cường hoá không gộp chồng) |

---

## Decision

**D1 — Ghi lúc bán, cùng transaction**: `SellItemUseCase` đọc cấp cường hoá của dòng (dưới khoá túi, TRƯỚC khi bớt — bán
hết thì dòng biến mất) và sau khi cộng vàng gọi `InventoryGatewayPort.recordSale` ⇒ bảng `shop_buyback` (V157) một dòng
`{món, số lượng, cấp cường hoá, giá = đúng số vàng đã nhận}`. Giữ **10** dòng gần nhất (cắt ngay lúc ghi). Hai hàm mới có
mặc định rỗng nên các fake ở test không phải sửa. Bán nhanh đồ rác (ADR-170) không đi qua đây — không ghi mua lại.

**D2 — Mua lại, một transaction**: khoá túi đồ (thứ tự khoá chung) → khoá dòng mua lại (của người gọi, bán trong **1
giờ**) → cấp lại món (món cường hoá mở ô riêng giữ cấp; món thường gộp chồng trước) → trừ đúng giá qua `GoldLedger`
(lý do `gold.reason.shop_buyback`, hiện trong sổ vàng) → xoá dòng → audit `SHOP_BUYBACK`. Túi đầy ⇒ `BAG_FULL` (không
đổi gì); thiếu vàng ⇒ lùi cả phần cấp món, `INSUFFICIENT_GOLD`. Dùng được ở mọi nơi, như lệnh bán.

**D3 — Giao thức, chỉ thêm**: `ListBuybackRequest/Response{entries}` 1036/1037; `BuybackCommand{entry_id}` /
`BuybackResponse{outcome, entries}` 1038/1039; `BuybackView{entry_id, item_template_id, quantity, enhance_level, price,
seconds_left}`.

**D4 — Client**: lệnh chat `/mualai` (hoặc `/buyback`) liệt kê có đánh số; `/mualai <số>` mua lại món số đó. Chưa có nút
trong cửa sổ cửa hàng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ghi cùng transaction bán (giữ cấp cường hoá), 10 món / 1 giờ, mua lại đúng giá, V157, 1036–1039, `/mualai` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): V157, `SoldItem`, `InventoryGatewayPort.enhanceLevelOf/recordSale`,
   `SellItemUseCase.sellIn`, `BuybackPort`/`BuybackFacade`, `PostgresBuybackAdapter`, `Lot34Commands`,
   `BuybackCoordinator`.
2. ✅ Test: `BuybackIntegrationTest` (Postgres thật: giữ +3, đúng giá, thiếu vàng lùi lại, quá hạn / người khác ⇒ không
   thấy), `BuybackCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (V157): ĐạoHữu01 bán 5 kunai ⇒ +10 vàng, `buybackList` ⇒ `#1 kunai x5 +0 giá=10`;
   `buyback:1` ⇒ `OK`, 209 `delta=-10 reason=gold.reason.shop_buyback`, kunai về đủ 20; mua lần hai ⇒ `NOT_FOUND`. Vàng và túi
   trở lại đúng như trước khi bán.
4. Chờ chủ dự án: 10 món / 1 giờ là số đề xuất.
