# ADR-389: Báo tin riêng khi ẩn chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Báo tin riêng khi ẩn chat" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: Đang ẩn khung chat (ADR-333) mà có tin riêng ⇒ chữ bay "Tin riêng: tên"). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-333](ADR-333-Hide-Chat-Key.md) · [ADR-247](ADR-247-Quick-Reply.md) |

---

## Decision

**D1:** khi khung chat đang ẩn (`ChatPanel.isHidden`, ADR-333) và có tin riêng từ người khác, hiện chữ bay `Tin riêng: <tên>` trên đầu nhân vật (màu emote). Tin vẫn vào nhật ký chat như thường; mở lại chat là đọc được.

**D2:** chỉ nghe `ChatMessageEvent`, không nuốt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, chữ bay khi chat ẩn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `ChatAlertsCoordinator.dispatch`.
2. ✅ Test: đường dùng chung chữ bay; kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
