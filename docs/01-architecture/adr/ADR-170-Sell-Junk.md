# ADR-170: Bán nhanh đồ rác

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Bán nhanh đồ rác" trong lượt hỏi lô 30 (AskUserQuestion; mô tả kèm: bán một lần cho NPC mọi món thuộc danh mục 'rác' (nguyên liệu quái rơi không dùng được), bỏ qua món đã khoá). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-159](ADR-159-Item-Lock.md) (món khoá không bị bán) · V55 (vật quest rơi 100% chỉ thu mua) |

---

## Decision

**D1 — "Rác" là DỮ LIỆU (V136)**: cột `item_template.junk` (mặc định false). Bộ đầu: **phi đao (kunai), sắt vụn, thẻ bài
ngoại môn, bùa Huyết Sa** — không dùng được, không làm nguyên liệu lò đan/luyện khí; hai món sau rơi 100% mãi mãi kể cả
khi quest đã xong (V55). Chỉ đánh dấu món có `shop_item.sell_price > 0`. Thêm/bớt = một câu UPDATE (Điều 27).

**D2 — Xem trước rồi mới bán**: sắt vụn/thẻ bài/bùa Huyết Sa còn là vật NỘP nhiệm vụ — người đang gom có thể không muốn
bán. Nên cửa sổ LIỆT KÊ trước chính xác cái sẽ bán + tổng vàng; chỉ bán khi bấm "Bán hết". Lúc bán server TỰ TÍNH LẠI
(không tin danh sách client).

**D3 — Điều kiện như bán lẻ ở cửa hàng + khoá**: chưa cường hoá, không khảm/tẩy luyện/linh thú, không đang mặc, không đang
chào giao dịch, **không khoá**. Giá = `shop_item.sell_price`. Một transaction: khoá túi → khoá các chồng → xoá → cộng vàng
(`GoldLedger`, `gold.reason.sell_junk`, ghi sổ vàng ADR-169) → audit `JUNK_SOLD`. Vàng chạm trần ⇒ rollback, báo
`gold_full`.

**D4 — Giao thức, chỉ thêm**: `GetJunkPreviewRequest/Response` (542/543), `SellJunkCommand/Response` (544/545).

**D5 — Client**: nút **Bán rác** trong túi đồ (hàng nút dưới cùng, nay 5 nút) hoặc `/banrac` mở cửa sổ: dòng đầu "Bán hết
— được N vàng" kèm nút, rồi từng loại "Sắt vụn x5 — 12 vàng mỗi cái".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cờ `junk` trong DB, xem trước rồi bán, bỏ qua món khoá/đang mặc/giao dịch, một transaction; 542–545 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): V136, `JunkSalePort`/`JunkSaleFacade`, `PostgresJunkSaleAdapter`, `JunkSaleCommands`
   542–545, nút Bán rác + `JunkSaleCoordinator` `/banrac`.
2. ✅ Test Postgres thật: `JunkSaleIntegrationTest` (chỉ bán rác chưa khoá, xem trước khớp cái bị bán, vàng đúng, lần hai rỗng).
3. ✅ Kiểm sống gameProbe 2026-11-04: xem trước `kunai x20 @2, sắt vụn x5 @12`; khoá chồng kunai ⇒ còn sắt vụn; bán ⇒ +60
   vàng (580 → 640), kunai khoá còn nguyên.
4. Chờ chủ dự án: danh sách món "rác" (D1).
