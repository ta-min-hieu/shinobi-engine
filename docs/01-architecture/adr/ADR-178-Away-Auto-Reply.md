# ADR-178: Tự trả lời khi vắng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-06) — chủ dự án chọn "Tự trả lời khi vắng" trong lượt hỏi lô 31 (AskUserQuestion; mô tả kèm: bật /vang <lời nhắn>, ai nhắn riêng thì tự gửi lại lời nhắn đó). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-091](ADR-091-Block-List.md) (chặn — người bị chặn không nhắn được nên không bao giờ nhận tự trả lời) · luật chat một dòng + bộ lọc từ cấm (2026-10-11) |

---

## Decision

**D1 — Trạng thái chỉ trong bộ nhớ gateway**: `/vang <lời nhắn>` treo lời nhắn; `/vang` trống thì tắt. Thoát game
(kết nối đóng) là tự hết vắng. Bảng lời nhắn là MỘT đối tượng dùng chung mọi kết nối (`ConnectedCharacterRegistry.away`)
— người vắng và người nhắn ở hai kết nối khác nhau, mà lớp lệnh gateway dựng lại theo từng kết nối. Không bảng, không migration — "vắng" là trạng thái của MỘT phiên chơi, khởi động lại
server mất cũng không sao.

**D2 — Lời nhắn**: một dòng (`ChatMessage.singleLine`), gộp khoảng trắng, tối đa **60 ký tự**, qua đúng bộ lọc từ cấm
của chat (`TOO_LONG` / `BANNED_WORD`, lời nhắn cũ giữ nguyên).

**D3 — Tự trả lời**: sau khi tin riêng đã giao cho người đang vắng, server gửi lại cho người gửi một
`ChatMessageEvent` kênh riêng, người gửi là người vắng, `auto_reply = true` (field MỚI 6). Không đi qua
`SendChatMessageCommand` nên không thể dội vòng; mỗi cặp (người vắng, người gửi) **tối đa một lần mỗi 5 phút**; không bao
giờ tự trả lời chính mình. Người bị chặn đã bị chặn từ bước nhắn riêng nên không chạm tới đây.

**D4 — Giao thức, chỉ thêm**: `SetAwayMessageCommand{message}` / `SetAwayMessageResponse{outcome, away, message}`
812/813; `ChatMessageEvent.auto_reply = 6`.

**D5 — Client**: `/vang` (hoặc `/away`); tin tự trả lời hiện nhãn "[Tự trả lời]" trước nội dung.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Vắng chỉ trong bộ nhớ phiên, ≤ 60 ký tự qua bộ lọc chat, trả lời 1 lần/5 phút mỗi người gửi, 812/813 + `auto_reply` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-06 (lô 31): `AwayCommands` (bộ nhớ, nhịp 5 phút, dọn khi `releaseCharacter`), tự trả lời trong
   `routeChatMessage` nhánh PRIVATE, `AwayCoordinator` `/vang`, nhãn trong `ChatCoordinator`.
2. ✅ Test: `AwayCommandsTest` (chuẩn hoá, trần, từ cấm, nhịp, tự mình, dọn), `AwayCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-06 (bước `setAway:<lời nhắn>`, `whisperAway:<tên>`): BaoDo01 treo "Đi luyện đan   về
   liền" ⇒ lưu "Đi luyện đan về liền"; ĐạoHữu01 nhắn riêng ⇒ nhận đúng MỘT tin `auto_reply=true` từ BaoDo01; nhắn lần
   hai ngay sau ⇒ không tự trả lời nữa (nhịp 5 phút); `setAway:` trống ⇒ tắt. **Lần kiểm sống đầu tìm ra lỗi thật**: bảng
   lời nhắn ban đầu nằm trong lớp lệnh dựng theo từng kết nối nên người nhắn không bao giờ thấy trạng thái của người
   vắng — đã chuyển lên `ConnectedCharacterRegistry` (dùng chung), test đơn vị không bắt được vì chỉ có một đối tượng.
4. Chờ chủ dự án: nhịp 5 phút / 60 ký tự; có muốn tự tắt vắng khi người chơi gõ chat lại không (hiện: không).
