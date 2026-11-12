# ADR-294: Cỡ chữ khung chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Cỡ chữ khung chat" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: /cochu nho|vua|lon). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-292](ADR-292-Chat-Idle-Fade.md) |

---

## Decision

**D1 — Thuần client.** `/cochu nho|vua|lon` (hoặc `/fontsize small|medium|large`) đặt hệ số chữ 0,85 / 1 / 1,2 cho
mọi dòng nhật ký chat + dòng ghim (`Label.setFontScale`, không dựng font mới). Nhớ trong Preferences `chat.font_size`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 3 cỡ chữ qua `setFontScale`; Preferences | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `ChatPanel.setFontScale`, `ChatLayoutCoordinator.FontSize`.
2. ✅ Test: `Lot45ClientTest.parsesFontSizes`.
3. ⏳ Thuần client — probe headless không vẽ UI; phủ bằng test đơn vị + biên dịch, cần kiểm tay trên client thật.
