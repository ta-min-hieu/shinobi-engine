# ADR-196: Theo dõi phường thị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Theo dõi phường thị" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: đánh dấu một loại vật phẩm; khi có người bày bán món đó dưới giá mình đặt thì được báo). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-063](ADR-063-Market.md) (phường thị) · [ADR-177](ADR-177-Market-History.md) |

---

## Decision

**D1 — Theo dõi gì**: một **loại vật phẩm** (mẫu) kèm **giá mỗi cái tối đa**. Giá mỗi cái của tin bán = giá tin chia số
lượng, **làm tròn lên** (không bao giờ báo một giá thấp hơn giá thật). Tối đa **10** loại mỗi nhân vật
(`MarketWatchFacade.MAX_WATCHES`); đặt lại cùng loại = sửa mức giá (không tính thêm dòng).

**D2 — Lưu**: V153 bảng `market_watch (character_id, item_template_id, max_unit_price, created_at)` + chỉ mục
`(item_template_id, max_unit_price)` cho câu tìm người cần báo. Trần kiểm sau khi khoá dòng nhân vật.

**D3 — Khi nào báo**: ngay sau khi một lệnh bày bán thành công (`MarketCommands.create`, Virtual Thread): lấy tin mới nhất
của người bán, tìm người theo dõi khớp, gửi `MarketWatchAlertEvent` cho ai **đang online** (người bán không tự nhận).
Offline thì không giữ lại — mở phường thị là thấy. Không báo lại khi tin cũ hạ giá (phường thị không có lệnh sửa giá).

**D4 — Giao thức, chỉ thêm**: `SetMarketWatchCommand{item_template_id, max_unit_price}` (0 = bỏ) /
`SetMarketWatchResponse` 1031/1032; `ListMarketWatchRequest`/`ListMarketWatchResponse{watches, max_watches}` 1033/1034;
`MarketWatchAlertEvent{listing_id, item_template_id, quantity, price, unit_price, seller_name}` 1035.

**D5 — Client**: lệnh chat `/theodoi` (hoặc `/watch`): không kèm gì = liệt kê; `/theodoi <giá mỗi cái> <tên hoặc mã
món>` = đặt / sửa; `/theodoi bo <tên hoặc mã món>` = bỏ. Báo hiện thành một dòng trong khung chat. Chưa có nút trong cửa
sổ phường thị.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Theo dõi theo mẫu + giá mỗi cái (làm tròn lên), `market_watch` (V153) trần 10, báo online lúc bày bán, 1031–1035 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): V153, `MarketWatchPort`/`MarketWatchFacade`, `PostgresMarketWatchAdapter`,
   `MarketCommands.alertWatchers`, `Lot33Commands.setMarketWatch/listMarketWatch`, `MarketWatchCoordinator`.
2. ✅ Test: `MarketWatchIntegrationTest` (Postgres thật), `MarketWatchCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07: ĐạoHữu01 `watch:17=5` ⇒ `OK`, mã mẫu lạ ⇒ `INVALID`; BaoDo01 bày 3 sắt vụn giá
   30 (10 mỗi cái > 5) ⇒ ĐạoHữu01 KHÔNG nhận báo; ĐạoHữu01 `watch:21=10`, BaoDo01 bày 5 lệnh bài giá 50 (10 mỗi cái) ⇒
   ĐạoHữu01 đang online nhận 1035. Hai tin bán thử đã gỡ, đồ trả lại túi, dòng theo dõi đã xoá.
4. ✅ Test fuzz opcode của gateway (`GatewayOpcodeFuzzDatabaseTest`) bắt được lỗi trước khi commit: mã mẫu vật phẩm không
   tồn tại làm vỡ khoá ngoại ⇒ 500. Nay adapter kiểm mẫu trước và trả `INVALID`.
5. Chờ chủ dự án: trần 10 loại là số đề xuất.
