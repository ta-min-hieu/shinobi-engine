# ADR-421: Ẩn bong bóng chat người lạ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Ẩn bong bóng chat người lạ" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: /bongbongban: bong bóng chat trên đầu chỉ hiện cho bạn bè và người cùng nhóm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-315](ADR-315-Chat-Bubbles.md) · [ADR-397](ADR-397-Friends-Only-Chat.md) |

---

## Decision

**D1:** khi bật, bong bóng chat khu vực (ADR-315) chỉ hiện cho chính mình, bạn bè (theo tên, cùng nguồn ADR-364) và người cùng nhóm (theo id, cùng nguồn ADR-285); tin vẫn vào khung chat như thường.

**D2:** `/bongbongban` (`/friendbubbles`), nhớ trên máy, mặc định tắt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, lọc người xem bong bóng | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `ChatBubbleCoordinator.useAudience`, `ChatTidyCoordinator.bubbleAllowed`.
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
