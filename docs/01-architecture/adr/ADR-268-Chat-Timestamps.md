# ADR-268: Giờ trước tin chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Giờ trước tin chat" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: Bật/tắt hiện [HH:mm] trước mỗi dòng chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | — |

---

## Decision

**D1 — Thuần client.** `ChatController.Line` thêm `receivedAtMillis` (giờ máy lúc dòng vào nhật ký; hàm dựng 3 tham số
cũ giữ nguyên). `ChatCoordinator.setTimestamps` vẽ `[HH:mm] ` trước mỗi dòng. `/giochat` (hoặc `/timestamps`) bật/tắt,
`bat | tat` đặt hẳn; lưu Preferences, mặc định TẮT. Nhật ký mang sang khi tự đăng nhập lại giữ đúng giờ cũ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `Line.receivedAtMillis`; `/giochat`; Preferences, mặc định tắt | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `ChatTimestampCoordinator`, `ChatCoordinator.timePrefix`.
2. ✅ Test: `Lot42ClientTest.timestampToggleParsesArguments`, `timePrefixUsesHoursAndMinutes`.
