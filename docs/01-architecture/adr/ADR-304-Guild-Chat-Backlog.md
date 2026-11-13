# ADR-304: Tin bang lúc vắng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Tin bang lúc vắng" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: server giữ 50 tin chat bang gần nhất; vào game thấy lại những tin bang mình bỏ lỡ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-192](ADR-192-Last-Online.md) · [ADR-266](ADR-266-Guild-Member-Online-Notice.md) |

---

## Decision

**D1 — RAM, không DB.** `GuildChatBacklog` giữ 50 tin bang gần nhất MỖI bang (ghi ngay sau khi phát tin kênh bang).
Server khởi động lại thì mất — đúng mức "món nhẹ"; lưu bền là việc riêng nếu cần.

**D2 — Phát lại:** vào world ⇒ lấy mốc "lần cuối rời world" (ADR-192, cột chỉ đổi khi rời world nên lúc vào vẫn là mốc
cũ) ⇒ gửi lại các tin bang sau mốc đó, đúng kênh bang, giữ tên người gửi, nội dung kèm giờ `[HH:mm UTC]`. Chưa từng rời
world (nhân vật mới) ⇒ không gửi. Không opcode mới (dùng `ChatMessageEvent`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ring buffer RAM 50 tin/bang; phát lại tin sau mốc rời world | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `GuildChatBacklog`, `Lot47Commands.onEnteredWorld`/`replay`, nhánh GUILD trong `GatewayChannelHandler`.
2. ✅ Test: `Lot47ServerTest.backlogKeepsNewestFiftyAndFiltersByTime`, `replayCarriesGuildChannelAndTime`.
3. ⏳ Kiểm sống gameProbe.
