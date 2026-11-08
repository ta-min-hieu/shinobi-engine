# ADR-250: Đánh dấu vị trí yêu thích

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Đánh dấu vị trí yêu thích" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: lưu toạ độ kèm tên, rồi tự đi tới (thuần client)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-206](ADR-206-Auto-Walk-To-Npc.md) (tự đi — cùng đường lái MoveIntent) |

---

## Decision

**D1 — Thuần client, lưu Preferences.** `/luuvitri <tên>` lưu toạ độ X hiện tại (world là một trục, ADR-033), tối đa 10
vị trí; `/vitri` liệt kê; `/vitri <tên>` tự đi tới (đúng đường tự đi ADR-206: client lái hướng −1/0/1, server chỉ thấy
`MoveIntent` thường — không dịch chuyển, không gì để lách); `/vitri xoa <tên>`. Tên so khớp không dấu. Lệnh `/den` của
ADR-206 giữ nguyên cho NPC (khỏi đụng tên NPC).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tối đa 10 vị trí X có tên trong Preferences; `/luuvitri`, `/vitri`; đi bằng đường tự đi ADR-206 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `LocationBookmarkCoordinator`.
2. ✅ Test: `Lot40bClientTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
