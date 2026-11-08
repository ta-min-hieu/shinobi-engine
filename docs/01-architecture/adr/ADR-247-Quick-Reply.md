# ADR-247: Trả lời nhanh tin riêng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Trả lời nhanh tin riêng" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: /r <nội dung> trả lời người vừa nhắn riêng cho mình (thuần client)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-187](ADR-187-Offline-Whispers.md) (tin riêng khi offline) |

---

## Decision

**D1 — Thuần client.** Client nhớ **tên người khác gần nhất** đã nhắn riêng cho mình (gói chat kênh riêng, người gửi
không phải chính mình — kể cả tin giữ lúc offline phát lại khi vào world). `/r <nội dung>` (hoặc `/reply`) gửi đúng một
tin riêng bình thường tới tên đó qua đường gửi chat sẵn có — server kiểm như mọi tin riêng (chặn, không làm phiền
ADR-236, hạn mức chat). Chưa ai nhắn ⇒ một dòng hướng dẫn. Không opcode.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhớ người nhắn riêng gần nhất; `/r` gửi tin riêng thường tới người đó | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `QuickReplyCoordinator`, `MentionWatch.isSelf`.
2. ✅ Test: `Lot40bClientTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
