# ADR-370: Báo bạn bè đổi lời nhắn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Báo bạn bè đổi lời nhắn" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: khi bạn đổi lời nhắn trạng thái (ADR-335), bạn bè đang online thấy một dòng báo). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-335](ADR-335-Status-Message.md) |

---

## Decision

**D1 — Khi nào:** đặt lời nhắn mới hợp lệ, không rỗng (ADR-335) ⇒ mọi bạn bè đang online nhận một dòng hệ thống "X đổi lời nhắn: …". Xoá lời nhắn hoặc chỉ hỏi ⇒ không báo. Việc phụ: lỗi không làm hỏng lệnh đặt.

**D2 — Không opcode mới** (dòng `ChatMessageEvent` hệ thống), không công tắc — người nhận đã có `/baobanbe` (ADR-311) cho dòng bạn bè vào game; tắt riêng dòng này để sau nếu cần.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đặt lời nhắn không rỗng ⇒ dòng báo cho bạn bè online; không opcode | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `Lot55Commands.notifyStatusChanged`/`statusLine` (gọi từ `Lot51Commands.setStatusMessage`).
2. ✅ Test: `Lot55ServerTest.statusLineNamesFriend`.
3. ✅ Kiểm sống gameProbe 2026-11-14: BaoDo01 đang online; ĐạoHữu01 đặt lời nhắn ⇒ BaoDo01 nhận "ĐạoHữu01 đổi lời nhắn: …"; xoá lời nhắn ⇒ không báo. Đã xoá lời nhắn thử.
