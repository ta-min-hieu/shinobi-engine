# ADR-395: Cuộn khung chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Cuộn khung chat" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: PageUp/PageDown xem lại các dòng chat cũ hơn). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-363](ADR-363-Chat-Visible-Lines.md) · [ADR-262](ADR-262-Chat-Line-Recall.md) |

---

## Decision

**D1:** nhật ký client giữ **40** dòng (trước: 12) để có chỗ cuộn; số dòng hiện vẫn 4–12 (ADR-363, nay trần riêng `ChatPanel.MAX_VISIBLE_LINES`). PageUp lùi một trang (số dòng hiện − 1), PageDown tiến; kẹp trong nhật ký. Tin mới tới khi đang cuộn: giữ chỗ đang xem (đếm từ cuối).

**D2:** không đổi protocol; nhãn dựng sẵn tái dùng, cuộn không cấp phát.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, nhật ký 40 dòng, PageUp/PageDown | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `ChatPanel.scroll/clampScroll/paintLines`, `ChatController.MAX_LINES = 40`.
2. ✅ Test: `Lot58ClientTest.clampsScrollInsideLog`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
