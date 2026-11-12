# ADR-292: Thu gọn khung chat khi rảnh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Thu gọn khung chat khi rảnh" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: khung chat tự mờ dần khi không có tin mới; chỉnh số giây). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | `ChatPanel.IDLE_HIDE_SECONDS` (playtest 2026-08-30) |

---

## Decision

**D1 — Từ "biến mất cái rụp" sang mờ dần.** Khung chat vốn tự ẩn sau 12 giây rảnh. Nay 2 giây cuối nó mờ dần
(`ChatPanel.idleAlpha`), tin mới hoặc mở ô nhập là đậm lại ngay.

**D2 — Chỉnh được:** `/chatan <giây>` (3–300) hoặc `/chatan tat` (luôn hiện); nhớ trong Preferences `chat.idle_seconds`.
Mặc định giữ 12 giây như cũ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mờ dần 2 giây cuối; `/chatan` 3–300 hoặc tắt; Preferences | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `ChatPanel.idleAlpha`/`setIdleHideSeconds`, `ChatLayoutCoordinator`.
2. ✅ Test: `Lot45ClientTest.chatFadesOnlyInTheLastSeconds`, `parsesIdleSecondsAndOffWords`.
3. ⏳ Thuần client — probe headless không vẽ UI; phủ bằng test đơn vị + biên dịch, cần kiểm tay trên client thật.
