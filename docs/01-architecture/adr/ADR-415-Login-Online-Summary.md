# ADR-415: Tóm tắt online khi vào game

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Tóm tắt online khi vào game" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: Vào world nhận một dòng "3 bạn bè và 5 thành viên bang đang online"). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-311](ADR-311-Friend-Presence-Chat-Line.md) · [ADR-383](ADR-383-Invisible-Mode.md) |

---

## Decision

**D1:** mỗi lần vào world, server đếm bạn bè đang hiện online (tôn trọng chế độ ẩn ADR-383 — bạn đang ẩn không được đếm) và thành viên bang đang online (trừ chính mình), gửi một dòng hệ thống nếu tổng > 0. Việc phụ: lỗi không làm hỏng vào world.

**D2:** không protocol mới — dòng đi bằng `ChatMessageEvent` hệ thống sẵn có.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng hệ thống lúc vào world, tôn trọng chế độ ẩn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `Lot61Commands.onEnteredWorld` gọi từ `Lot47Commands.onEnteredWorld`.
2. ✅ Test: kiểm sống.
3. ✅ Kiểm sống 2026-11-14: BaoDo01 (bạn bè + cùng bang) đang online, ĐạoHữu01 vào world ⇒ nhận dòng "Đang online: 1 bạn bè, 1 đồng môn".
