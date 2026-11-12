# ADR-284: Lịch sử tin riêng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Lịch sử tin riêng" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: /tinrieng <tên>: xem lại các tin riêng đã trao đổi với người đó trong phiên). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-247](ADR-247-Quick-Reply.md) (trả lời nhanh) |

---

## Decision

**D1 — Thuần client, RAM.** Tin **nhận** lấy từ gói chat kênh riêng (người gửi không phải mình); tin **gửi** lấy từ đường
gửi của ô nhập (`ChatCoordinator.onOutgoing` — server không dội tin riêng về người gửi). Mỗi người tối đa 30 dòng,
khoá tên không dấu. `/tinrieng <tên>` (hoặc `/whispers`) in lại; không kèm tên ⇒ danh sách người đã nhắn. Tin gửi bằng
`/r` (ADR-247) không đi qua ô nhập nên **chưa** được ghi — giới hạn đã biết.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | RAM, 30 dòng/người, tin nhận từ gói + tin gửi từ ô nhập; `/tinrieng` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `WhisperHistoryCoordinator`, `ChatCoordinator.onOutgoing`.
2. ✅ Test: không có test riêng (lớp cần `ChatCoordinator` trên libGDX).
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
