# ADR-363: Số dòng khung chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Số dòng khung chat" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: /dongchat 4–12: đổi số dòng khung chat hiển thị (hiện cố định 6 dòng), nhớ trên máy). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-294](ADR-294-Chat-Font-Size.md) · [ADR-292](ADR-292-Chat-Idle-Fade.md) |

---

## Decision

**D1 — Nhật ký** `ChatController` giữ tới 12 dòng (trước 6); `ChatPanel` dựng sẵn 12 nhãn và chỉ đưa N nhãn CUỐI vào bảng — dòng mới nhất luôn ở đáy nên đường vẽ không đổi, không tạo actor mới.

**D2 — Mặc định vẫn 6 dòng** như trước; `/dongchat 4-12` (`/chatlines`) đổi và nhớ trong Preferences. Thuần client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhật ký 12 dòng, hiện N nhãn cuối (mặc định 6); `/dongchat`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `ChatController.MAX_LINES = 12`, `ChatPanel.setVisibleLines`, `ChatLinesCoordinator`.
2. ✅ Test: `ChatControllerTest` (theo hằng `MAX_LINES`).
3. ⏳ Thuần client — cần chạy client thật để nhìn khung chat.
