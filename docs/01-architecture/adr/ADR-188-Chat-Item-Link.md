# ADR-188: Khoe vật phẩm vào chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Khoe vật phẩm vào chat" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: chèn một món trong túi vào tin chat; người khác bấm để xem chỉ số, cường hoá, ngọc của món đó). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-171](ADR-171-Inspect-Player.md) (soi trang bị — cùng nguồn dữ liệu món) · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (chỉ số hiệu dụng do server tính) |

---

## Decision

**D1 — Server đọc món thật**: `SendChatMessageCommand.linked_item_id = 4` là id một món **trong túi / trên người** người gửi;
server đọc món đó (mẫu, cường hoá, ngọc khảm, số lượng, **chỉ số hiệu dụng** `EnhancementTable.enhancedBonus`) và gắn
`ChatMessageEvent.linked_item = 8` (`LinkedItemView`) — ảnh chụp lúc gửi. Client không khai chỉ số; món không có ⇒ outcome
MỚI `LINKED_ITEM_NOT_FOUND = 14`. Tin vẫn đi qua mọi kiểm duyệt + audit của chat như thường.

**D2 — Không dùng ở kênh Khu vực**: tin Khu vực đi qua world tick (`AreaChatMessageCommand`) và gói AoI của nó không mang
món ⇒ outcome `LINKED_ITEM_NOT_ON_AREA = 15` (thay vì âm thầm làm rơi món). Thế giới / bang / nhóm / kết nghĩa / riêng
đều được.

**D3 — Hiển thị**: client người nhận ghi nhãn **"[Tên +N | Ngọc xS]" TRƯỚC nội dung**, dựng từ `linked_item` server gửi —
chữ người gửi gõ không giả được nhãn. Tin thường không thêm byte nào (field chỉ gắn khi có món).

**D4 — Client**: chọn món trong túi rồi `/khoe [lời nhắn]` (hoặc `/show`) — khoe ở kênh đang chọn; `/xemmon` (hoặc
`/iteminfo`) in chỉ số hiệu dụng của món khoe gần nhất thấy trong chat. ⚠️ Lệch mô tả lúc hỏi ("bấm để xem"): khung chat
hiện chưa có dòng bấm được ⇒ xem bằng `/xemmon`; nhãn trong dòng chat đã có tên, cường hoá, ngọc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Server đọc món thật + chỉ số hiệu dụng, gắn `LinkedItemView` vào tin; không ở kênh Khu vực; 2 outcome + 2 field | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): `ItemLinkCommands` (đọc món), `linkedItemOf` trong `handleSendChatMessageBlocking`,
   `ChatRouteContext.linkedItem`, nhãn trong `ChatCoordinator`, `ItemLinkCoordinator` `/khoe` `/xemmon`.
2. ✅ Test: `ItemLinkCoordinatorTest` (nhãn), `ChatControllerTest` (mọi outcome có câu).
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `showItem:<id>`, `showItemResult:<id>`, `showItemArea:<id>`): ĐạoHữu01 khoe
   món #1 của BaoDo01 ⇒ `LINKED_ITEM_NOT_FOUND`; khoe trường kiếm #21 ở Khu vực ⇒ `LINKED_ITEM_NOT_ON_AREA`; ở Thế giới ⇒
   tin dội về mang `linked_item` mẫu 10, +0, công 18 (server tính).
4. Sau này: dòng chat bấm được để mở khung chi tiết món; mang món qua kênh Khu vực (cần đổi lệnh world).
