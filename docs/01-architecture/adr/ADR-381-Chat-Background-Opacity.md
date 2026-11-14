# ADR-381: Độ mờ nền khung chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Độ mờ nền khung chat" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: /nenchat 0-100: độ đậm nền khung chat, chữ giữ nguyên). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-292](ADR-292-Chat-Idle-Fade.md) · [ADR-363](ADR-363-Chat-Visible-Lines.md) |

---

## Decision

**D1:** nền khung chat bọc trong `FadedDrawable` (nhân alpha của batch khi vẽ nền, không cấp phát mỗi khung); chữ không đổi. Kết hợp với mờ dần khi rảnh (ADR-292) vì alpha nhân dồn.

**D2:** `/nenchat <0-100>` (`/chatbg`), nhớ trên máy, mặc định 100. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, `FadedDrawable` bọc nền | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `FadedDrawable`, `ChatPanel.setBackgroundOpacity`, `ChatToolsCoordinator`.
2. ✅ Test: kiểm bằng mắt; cú pháp sai in hướng dẫn.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
