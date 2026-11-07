# ADR-187: Nhắn riêng khi offline

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Nhắn riêng khi offline" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: nhắn riêng cho người đang offline thì server giữ tin (tối đa 20 tin, 7 ngày), họ nhận khi vào game). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-091](ADR-091-Block-List.md) (chặn) · [ADR-052](ADR-052-In-Game-GM-Commands.md) (khoá chat) · luật chat một dòng + bộ lọc từ cấm + audit (2026-10-11) |

---

## Decision

**D1 — Cùng đường kiểm duyệt**: tin riêng tới người offline KHÔNG còn bị trả `TARGET_OFFLINE` ngay; nó đi đúng mọi bước
của tin thường — chặn nhau, nhịp chống spam, khoá chat của GM, một dòng, độ dài, từ cấm, **audit** — rồi mới được giữ
lại thay vì phát. Outcome MỚI `DELIVERED_LATER = 12` (giữ được) / `RECIPIENT_INBOX_FULL = 13`. `TARGET_OFFLINE` ở lại
trong enum cho tương thích, server mới không trả nữa.

**D2 — Hộp tin chờ (V148)**: `offline_whisper(recipient_id, sender_id, body, created_at)`; **tối đa 20 tin / người nhận**
(đếm sau khi khoá dòng nhân vật người nhận — hai người gửi cùng lúc không vượt trần); **giữ 7 ngày** (bỏ khi giao +
`RetentionJanitor`). Xoá theo nhân vật.

**D3 — Giao khi vào game**: bước "vào world" lấy ra và xoá mọi tin còn hạn, cũ trước, gửi từng tin như
`ChatMessageEvent` kênh riêng với field MỚI `sent_at_epoch_seconds = 7`; client ghi "[Gửi lúc dd/MM HH:mm]" trước nội
dung. Hai người đã chặn nhau từ lúc gửi tới lúc giao ⇒ tin bị bỏ. Việc phụ: lỗi không làm hỏng lần vào game.

**D4 — Không có opcode mới**: chỉ hai giá trị enum + một field. `protocolVersion` không tăng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tin offline qua đủ kiểm duyệt + audit rồi giữ (V148), 20 tin / 7 ngày, giao lúc vào game kèm giờ gửi; 2 outcome + 1 field | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): V148, `OfflineWhisperPort`/`OfflineWhisperFacade` (feature-chat),
   `PostgresOfflineWhisperAdapter`, `OfflineWhisperCommands` (giữ + giao lúc vào world), `resolvePrivateTarget` tách
   khỏi `handleSendChatMessageBlocking`, nhãn giờ gửi trong `ChatCoordinator`.
2. ✅ Test: `OfflineWhisperIntegrationTest` (Postgres thật: trần, thứ tự, lấy một lần, bỏ quá hạn); `ChatControllerTest`
   (mọi outcome có câu).
3. ✅ Kiểm sống gameProbe 2026-11-07: BaoDo01 offline, ĐạoHữu01 nhắn riêng hai lần ⇒ `DELIVERED_LATER` ×2; BaoDo01 vào
   game ⇒ nhận hai `ChatMessageEvent` kênh riêng từ ĐạoHữu01; vào lần hai ⇒ 0 tin, bảng `offline_whisper` rỗng. **Kiểm toàn
   bộ tìm ra một chỗ phải giữ**: composition không có hộp tin (`DISABLED`, test) trả "hộp đầy" — đã sửa để trả lại
   `TARGET_OFFLINE` như cũ (`ChatWireTest` đóng đinh hành vi đó).
4. Giới hạn đã biết: tin đã lấy ra rồi mà người nhận rớt mạng đúng giây đó thì mất (như tin thường gửi trúng lúc rớt).
