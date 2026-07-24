# CLAUDE.md — feature-chat

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-chat/**`.

## Cấu trúc (Clean Arch)

```
api/            ChatFacade (+ SendResult — enum RIÊNG, không lộ type UseCase ra ngoài)
application/    SendChatMessageUseCase
domain/         ChatChannel (enum) · ChatMessage (VO, không lưu DB) · ProfanityFilter (utility)
                ChatAuditPort
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresChatAuditAdapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-23, Phase 6 lát cắt 6)

- **Kênh Bang hội** thêm ở Phase 7 lát cắt 1 cùng `feature-guild` thật (`ChatChannel.GUILD`) — định
  tuyến vẫn KHÔNG thuộc feature-chat (xem mục dưới), gateway tự gọi `GuildFacade.listMembers` sau
  khi gửi OK. **Kênh Party** thêm ở lát cắt 2 cùng `feature-party` thật (`ChatChannel.PARTY`,
  2026-07-24) — cùng cơ chế, gateway gọi `PartyFacade.listMembers`.
- **Không lưu lịch sử chat** — không có bảng riêng. Mỗi tin nhắn HỢP LỆ ghi 1 dòng `audit_log`
  (action `CHAT_MESSAGE`) làm dấu vết cho báo cáo (GAMEPLAY.md §10), KHÔNG xây UI xem báo cáo.
- **Định tuyến người nhận KHÔNG thuộc feature này** — World (toàn server), Riêng tư (1 người,
  resolve tên qua `CharacterFacade`), Khu vực (AoI) đều cần dữ liệu kết nối/world mà feature module
  không được biết (Điều 23: không Netty/không world tick). `server-gateway`/`server-world` tự định
  tuyến SAU KHI `ChatFacade.sendMessage` trả `OK` — cùng nguyên tắc `AttackResultEvent` đã dùng.
- **Khu vực (AREA) cần tích hợp world tick thật** — `WorldFacade` có thêm
  `enqueueAreaChatMessage`/`WorldCommand.AreaChatMessageCommand`, xử lý ở `drainInbox` gọi
  `aoiTracker.observersOf(senderCharacterId)` (an toàn vì chạy trên tick thread), rồi phát qua
  `WorldOutboundSink.onAreaChatMessage` — do `visibleTo` map của `AreaOfInterestTracker` KHÔNG
  thread-safe, KHÔNG được gọi trực tiếp từ Netty/Virtual Thread (server/CLAUDE.md: "ECS state chỉ
  sửa/đọc trên tick thread"). World/Riêng tư KHÔNG cần bước này (không phụ thuộc AoI).
- **Lọc từ cấm PLACEHOLDER** — danh sách vài từ mẫu, không homoglyph-aware, cùng tinh thần validate
  tên nhân vật ở feature-character (dự án cá nhân, rủi ro thấp, để mức tối thiểu có chủ đích).
- **XSS KHÔNG xử lý ở server** — client hiển thị chat dạng text thuần (không render HTML), nên
  không cần escape phía server (khác cảnh báo GAMEPLAY.md §10 dành cho client renderer HTML nếu có).

## Cứng

✅ Validate độ dài/rỗng/từ cấm ở `application/` + `domain/` (compact constructor `ChatMessage`)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Ghi audit cho MỌI tin nhắn hợp lệ, KHÔNG lưu nội dung ở bảng riêng

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ Tự định tuyến người nhận (xem mục quyết định ở trên)

## Test

`domain/` test không mock (`ChatMessageTest`, `ProfanityFilterTest`). `application/` test với
**Fake Port** (không Mockito).
