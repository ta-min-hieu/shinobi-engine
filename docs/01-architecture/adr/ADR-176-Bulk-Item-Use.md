# ADR-176: Dùng nhiều vật phẩm một lần

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-06) — chủ dự án chọn "Dùng nhiều vật phẩm" trong lượt hỏi lô 31 (AskUserQuestion; mô tả kèm: dùng một lúc N món chồng nhau (mở túi phúc, đan EXP...) thay vì bấm từng cái). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) (dùng vật phẩm 512/513) · [ADR-163](ADR-163-Lucky-Bag.md) (túi phúc) · [ADR-164](ADR-164-Double-Exp-Pill.md) (đan x2) |

---

## Decision

**D1 — Lặp đúng đường dùng một món**: server lặp `InventoryFacade.useItem` (hoặc `LuckyBagFacade.open`) tối đa `count`
lần, **dừng ở lần đầu bị từ chối** và báo lý do. Mỗi lần là một transaction + audit riêng như bấm tay ⇒ dừng giữa chừng
không để lại trạng thái dở, không có luật mới để lách. `count` kẹp **1..50** phía server.

**D2 — Món nào**: chỉ hiệu ứng `DOUBLE_EXP` và `EXPAND_BAG` (và túi phúc qua lệnh riêng). **Không** đan hồi máu (hồi
chiêu chiến đấu 10 giây — dùng nhiều là lách hồi chiêu) và **không** tiến hoá linh thú (chỉ một lần) ⇒
`error.item_not_bulk_usable`. Vì thế lệnh nhiều món KHÔNG đi qua hồi chiêu dùng vật phẩm (`ItemUseCooldown`): món nào có
hồi chiêu thì không dùng nhiều được.

**D3 — Giao thức, chỉ thêm**: `UseItemsCommand{inventory_item_id, count}` / `UseItemsResponse{used, remaining_quantity,
stop_error_key}` 552/553; `OpenLuckyBagsCommand{bag_item_template_id, count}` / `OpenLuckyBagsResponse{opened, rewards
(gộp theo loại), stop_outcome, bags}` 554/555. Không đổi DB.

**D4 — Client**: cửa sổ túi phúc (`/tuiphuc`) có thêm dòng **"Mở 10"** dưới mỗi loại túi khi có từ hai túi; món trong
túi đồ: chọn món rồi gõ `/dungnhieu [số]` (hoặc `/usemany`; bỏ trống = tối đa 50). Dùng đan x2 xong HUD hạn x2 được báo
lại (ADR-164).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lặp đúng đường một món, dừng ở lần từ chối đầu, kẹp 1..50; chỉ đan x2 / mở rộng túi / túi phúc; 552–555 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-06 (lô 31): `BulkUseCommands` 552–555, dòng "Mở 10" trong `LuckyBagCoordinator`,
   `BulkUseCoordinator` `/dungnhieu`.
2. ✅ Test: `BulkUseCommandsTest` (kẹp số lần, loại món), `BulkUseCoordinatorTest` (đối số, câu báo).
3. ✅ Kiểm sống gameProbe 2026-11-06 (bước `useMany:<id>x<n>`, `openBags:<mẫu>x<n>`): đan hồi máu ⇒ `dùng=0`,
   `error.item_not_bulk_usable`; 5 đan x2 dùng 3 ⇒ còn 2; dùng 10 ⇒ dùng 2 rồi dừng vì hết (ô biến mất); 4 Túi Phúc mở 10
   ⇒ mở 4, quà gộp (1600 vàng + hai loại món), dừng `NO_BAG`; mở tiếp ⇒ 0.
4. Sau này: nút "Dùng hết" trong túi đồ khi hàng nút có chỗ.
