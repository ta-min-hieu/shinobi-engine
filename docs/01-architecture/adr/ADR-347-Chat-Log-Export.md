# ADR-347: Lưu chat ra file

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lưu chat ra file" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: /luuchat ghi toàn bộ nhật ký chat đang có ra một file văn bản trên máy). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-317](ADR-317-System-Notice-History.md) · [ADR-229](ADR-229-Chat-Channel-Filter.md) |

---

## Decision

**D1 — Giữ:** khung chat chỉ giữ 6 dòng, nên luồng này tự giữ 500 tin gần nhất của phiên (mọi `ChatMessageEvent` tới, kể cả dòng hệ thống từ server) trong RAM.

**D2 — Ghi:** `/luuchat` (`/savechat`) ghi `chatlogs/chat-<ngày-giờ>.txt` cạnh game (UTF-8), mỗi dòng `[giờ] (kênh) người gửi: nội dung`; báo số dòng và đường dẫn. Thuần client, không gửi gì lên server.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bộ đệm 500 tin của phiên, ghi file local UTF-8; `/luuchat`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `ChatLogExportCoordinator`.
2. ✅ Test: `Lot52ClientTest.formatsChatLogLine`.
3. ⏳ Thuần client — cần chạy client thật để ghi file.
