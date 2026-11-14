# ADR-364: Tô màu tên bạn bè trong chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Tô màu tên bạn bè trong chat" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: tên người gửi là bạn bè của bạn được đánh dấu (ký hiệu/màu riêng) trong khung chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-245](ADR-245-Mention-Highlight.md) · [ADR-339](ADR-339-Chat-Channel-Colors.md) |

---

## Decision

**D1 — Màu:** dòng chat kênh công khai (không phải tin riêng, không phải hệ thống) của người có trong danh sách bạn bè đang giữ ⇒ loại dòng mới `FRIEND`, màu xanh lá mạ. Nhắc tên mình (ADR-245) vẫn được ưu tiên. Tên so sau khi bỏ tiền tố ký hiệu bang/danh hiệu.

**D2 — Đổi màu được** bằng `/maukenh banbe <màu>` (ADR-339). Thuần client, không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Loại dòng `FRIEND` cho tin bạn bè ở kênh công khai; đổi màu qua `/maukenh banbe`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `ChatPanel.LineKind.FRIEND`, `ChatCoordinator.lineKind`/`bareName`/`useFriendNames`, `FriendController.hasFriendNamed`, `ChatLinesCoordinator.useFriends`.
2. ✅ Test: `Lot54ClientTest.bareNameDropsTagAndTitle`.
3. ⏳ Thuần client — cần chạy client thật để nhìn màu.
