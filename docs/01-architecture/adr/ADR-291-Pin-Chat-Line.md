# ADR-291: Ghim tin chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Ghim tin chat" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: /ghimchat: ghim tin gần nhất lên đầu khung chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-262](ADR-262-Chat-Line-Recall.md) (khung chat) |

---

## Decision

**D1 — Thuần client.** `/ghimchat` (hoặc `/pinchat`) ghim **dòng người chơi gần nhất** (bỏ dòng hệ thống) lên một nhãn
riêng ở đầu `ChatPanel`, màu vàng nhạt; `/ghimchat bo` bỏ ghim. Chỉ sống trong phiên, không lưu.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhãn ghim trong `ChatPanel`; `/ghimchat [bo]` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `ChatPanel.setPinnedLine`, `ChatCoordinator.latestPlayerLine`, `ChatLayoutCoordinator`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Thuần client — probe headless không vẽ UI; phủ bằng test đơn vị + biên dịch, cần kiểm tay trên client thật.
