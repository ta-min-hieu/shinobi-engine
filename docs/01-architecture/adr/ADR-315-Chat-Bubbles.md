# ADR-315: Bong bóng chat trên đầu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Bong bóng chat trên đầu" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: tin kênh khu vực hiện thành bong bóng chữ trên đầu người nói vài giây (dùng lại lớp vẽ biểu cảm)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-092](ADR-092-Emotes.md) |

---

## Decision

**D1 — Thuần client.** Tin kênh KHU VỰC của người chơi (đã qua bộ lọc chặn) hiện thành chữ trên đầu người nói, dùng
lại lớp vẽ biểu cảm (`EmoteBubbles`, ADR-092; người ngoài tầm nhìn thì bỏ qua), cắt gọn 40 ký tự. `/bongbong` (hoặc
`/bubbles`) bật/tắt, nhớ trong Preferences.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tin khu vực ⇒ chữ trên đầu qua `EmoteBubbles`; `/bongbong` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `ChatBubbleCoordinator`, `ChatCoordinator.onIncoming`.
2. ✅ Test: `Lot48ClientTest.bubbleTextIsTrimmed`.
3. ⏳ Thuần client — probe headless không vẽ UI/không chạy lệnh gạch chéo; phủ bằng biên dịch + test đơn vị, cần kiểm tay trên client thật.
