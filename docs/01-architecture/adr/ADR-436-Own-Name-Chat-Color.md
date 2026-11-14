# ADR-436: Màu tên mình trong chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Màu tên mình trong chat" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: Dòng chat do chính mình gửi có màu riêng; /mautenminh <màu> đổi màu). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-339](ADR-339-Chat-Channel-Colors.md) |

---

## Decision

**D1 — Loại dòng:** thêm `ChatPanel.LineKind.SELF` (mặc định xanh nhạt `8FE3FF`); `ChatCoordinator` gán SELF khi người gửi là chính mình (`isOwnName`), ưu tiên trước màu kênh.

**D2 — Đổi màu:** tái dùng `/maukenh` (ADR-339) với khoá mới `minh`; `/mautenminh <màu>` (`/mycolor`) là viết tắt của `/maukenh minh <màu>` (line rewriter). Chỉ client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Chỉ client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `ChatPanel.LineKind.SELF`, `ChatCoordinator.lineKind`, `ChatColorCoordinator` (`minh`), `HudPolishCoordinator.colorAlias`.
2. ✅ Test: `Lot63ClientTest.aliasesOwnNameColour`.
3. Chỉ client — `Lot63ClientTest` xanh.
