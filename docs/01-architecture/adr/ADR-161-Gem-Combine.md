# ADR-161: Ghép Linh Ngọc

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "Ghép Linh Ngọc" trong lượt hỏi lô 29 (AskUserQuestion; mô tả kèm: 3 viên Linh Ngọc cùng loại cùng cấp + vàng ⇒ 1 viên cấp cao hơn (100% thành công). Nối với khảm nạm ADR-064). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-064](ADR-064-Spirit-Jade-Sockets.md) (khảm nạm — ngọc bậc mới khảm được ngay) · [ADR-159](ADR-159-Item-Lock.md) (ngọc khoá không bị đem ghép) |

---

## Decision

**D1 — Dữ liệu (V129)**: trước lô này mỗi loại ngọc chỉ có MỘT bậc ⇒ thêm **bậc 2 (Nhị phẩm) và bậc 3 (Tam phẩm)** cho
cả 4 loại (8 `item_template` + 8 dòng `gem_template`): Hoả/Thổ/Thuỷ 3 → 8 → 20, Mộc (máu) 15 → 40 → 100. Bảng
`gem_combine_recipe(source PK, result, source_quantity=3, gold_fee, sort_order)`: bậc 1→2 phí 2.000 vàng, bậc 2→3 phí
8.000. Bậc 3 là trần. Mọi số là PLACEHOLDER cân bằng (Điều 27).

**D2 — Ghép**: một lần ghép = một transaction: khoá túi → trừ N viên nguồn qua `TemplateQuantityConsumer.consumeUnlocked`
(đường "nguyên liệu": không lấy chồng đã khoá, không lấy phần đang chào giao dịch, không lấy viên đang khảm — viên khảm
là dòng `item_socket`, không phải dòng túi) → trừ vàng (`GoldLedger`, lý do `gold.reason.gem_combine`) → thêm viên mới
(gộp chồng hoặc mở dòng) → audit `GEM_COMBINED`. Hỏng bước nào thì rollback cả lần. 100% thành công — không random.

**D3 — Giao thức, chỉ thêm**: `GetGemRecipesRequest/Response` (532/533, kèm `owned` = số viên đem ghép được),
`CombineGemsCommand/Response` (534/535; outcome OK / UNKNOWN_RECIPE / NOT_ENOUGH_GEMS / NOT_ENOUGH_GOLD / BAG_FULL;
kèm bảng sau lần ghép).

**D4 — Client**: cửa sổ mở bằng `/ghepngoc` (hoặc `/gemcombine`): mỗi công thức một dòng "3 Hoả Linh Ngọc + 2000 vàng
= Hoả Linh Ngọc (Nhị phẩm) (đang có 4)" kèm nút Ghép (mờ khi chưa đủ viên).

**D5 — Nợ art**: 8 viên mới chưa có icon nguồn (cần Blender — `tools/blender/build_item_icon.py` đã có hàm
`_spirit_jade`, chỉ cần thêm biến thể màu đậm hơn) ⇒ ghim vào `ITEMS_KNOWN_TO_LACK_AN_ICON`; trong túi chúng hiện ô
trống tới khi có icon.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thêm bậc 2/3 cho 4 loại ngọc; 3 viên + vàng ⇒ 1 viên bậc trên, 100%; ngọc khoá không bị ghép; 532–535 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): V129, `GemCombinePort`/`GemCombineFacade`, `PostgresGemCombineAdapter`,
   `GemCombineCommands` 532–535, `GemCombineCoordinator` `/ghepngoc`.
2. ✅ Test Postgres thật: `GemCombineIntegrationTest` (3 ô: ghép đúng số + vàng; thiếu vàng không mất ngọc; ngọc khoá
   không bị ghép + bậc 3 không có công thức).
3. ✅ Kiểm sống gameProbe 2026-11-03 (`--lot21=gemRecipes,combineGem:37,combineGem:37`): 4 Hoả Linh Ngọc, 3.240 vàng ⇒
   `COMBINE_GEMS_OUTCOME_OK ra=81`, còn 1 viên bậc 1 + 1 viên Nhị phẩm, vàng 1.240; lần 2 ⇒ `NOT_ENOUGH_GEMS`.
4. Chờ chủ dự án: chỉ số/phí từng bậc (PLACEHOLDER); duyệt chữ `gem_combine.*` và tên "Nhị phẩm/Tam phẩm"; icon 8 viên
   mới (cần Blender).
