# ADR-159: Khoá vật phẩm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "Khoá vật phẩm" trong lượt hỏi lô 29 (AskUserQuestion; mô tả kèm: bấm khoá một món: món bị khoá không bán cho NPC, không phân giải, không đăng phường thị, không giao dịch, không vứt được — chống mất đồ quý vì bấm nhầm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-064](ADR-064-Spirit-Jade-Sockets.md) (luật "món đã khảm không dời được" — cùng cách chặn) · [ADR-115](ADR-115-Security-Pin.md) (cổng Mật Khẩu Bảo Hộ) · dòng 80 docs/README (phần đang chào giao dịch) |

---

## Decision

**D1 — Dữ liệu (V128)**: cột `inventory_item.locked BOOLEAN NOT NULL DEFAULT FALSE`. Dòng cũ = chưa khoá, không đổi
hành vi. Không bảng mới: khoá là thuộc tính của ĐÚNG một dòng túi, đi theo dòng đó (gộp chồng vào một chồng khoá thì
phần gộp vào cũng khoá).

**D2 — Phạm vi chặn: món khoá được đối xử NHƯ MÓN ĐANG MẶC ở mọi đường làm nó RỜI nhân vật hoặc bị ĐỔI lấy thứ khác**
— bán NPC, giao dịch, thư, phường thị (đăng bán + bán vào lệnh mua), kho tông môn, phân giải, nộp vật phẩm nhiệm vụ,
nguyên liệu lò đan. Câu kiểm nằm trong CHÍNH câu SQL "đang mặc" sẵn có của từng đường (cùng `FOR UPDATE`, sau
`CharacterInventoryLock`), nên không có khe "vừa khoá xong vẫn bán được": bật/tắt khoá cũng lấy khoá túi trước.
Chỉ có 7 chỗ trong toàn bộ server-database xoá/dời dòng `inventory_item` — đã rà đủ cả 7.

**D3 — DÙNG trực tiếp vẫn được** (uống đan, dùng phù, khảm ngọc, phù bảo hộ khi cường hoá): người chơi chủ động dùng
chính món đó, không phải "mất vì bấm nhầm" mà khoá nhắm tới. `TemplateQuantityConsumer`/`InventoryItemTake` tách hai
cửa: `consume`/`takeByCode` (dùng — bỏ qua khoá) và `consumeUnlocked`/`takeUnlockedByCode` (thư, lò đan, nhiệm vụ).

**D4 — Mã từ chối**: các đường cũ trả lại mã "đang mặc"/"không bán được" sẵn có (không thêm giá trị enum vào 6 response
khác nhau); RIÊNG kho tông môn có `STORAGE_OUTCOME_LOCKED = 10` vì kho có câu riêng cho từng lý do. Client nói rõ
trước: bấm Bán/Giao dịch/Phân giải trên món khoá ⇒ "Món này đang khoá — bấm Mở khoá trước", không gửi lệnh.

**D5 — Giao thức, chỉ thêm**: `InventoryItemView.locked = 7`; `SetItemLockCommand`/`SetItemLockResponse` (528/529,
outcome OK / NOT_FOUND). `characterId` từ session; món của người khác ⇒ NOT_FOUND. Lệnh đi cổng Mật Khẩu Bảo Hộ
(`pinGuarded`) như các đường làm đồ rời túi — kẻ chiếm phiên không mở khoá được. Audit `ITEM_LOCKED`/`ITEM_UNLOCKED`.

**D6 — Client**: túi đồ có nút **Khoá / Mở khoá** (hàng dưới cùng, cạnh Phân giải) cho món đang chọn; dòng món khoá
ghi `[Khoá] …`. Kết quả về `ItemLockCoordinator` (dòng hệ thống + hỏi lại túi).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `locked` trên dòng túi; chặn như món đang mặc ở mọi đường rời túi; dùng trực tiếp vẫn được; 528/529 qua cổng PIN | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): V128, `ItemLockPort`/`ItemLockFacade`, `PostgresItemLockAdapter`, guard trong
   Shop/Trade/Market/Dismantle/Mail/Crafting/Quest/Storage, `ItemLockCommands` 528/529, nút Khoá trong túi đồ.
2. ✅ Test Postgres thật: `ItemLockIntegrationTest` (4 ô: bán bị chặn rồi mở khoá bán được; giao dịch bị chặn; thư/
   nguyên liệu/nộp nhiệm vụ bị chặn nhưng dùng trực tiếp được; khoá món người khác = NOT_FOUND + audit).
3. ✅ Kiểm sống gameProbe 2026-11-03 (`--lot21=lock:5,bag,sell:5,dismantle:5,unlock:5,bag,sell:6`): khoá OK, túi hiện
   `5:2x1[LOCK]`, bán ⇒ `SELL_OUTCOME_ITEM_NOT_SELLABLE`, phân giải ⇒ `DISMANTLE_ITEM_OUTCOME_EQUIPPED`, mở khoá OK,
   bán thanh kia ⇒ `SELL_OUTCOME_OK`.
4. Chờ chủ dự án: duyệt chữ `item_lock.*`; có muốn cấm cả DÙNG trực tiếp món khoá (D3) không.
