# ADR-295: Chuyển tiếp thư

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Chuyển tiếp thư" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: chuyển nội dung một thư đã nhận cho người khác (không kèm đồ/vàng)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-275](ADR-275-Mail-Search.md) · [ADR-212](ADR-212-Stranger-Mail-Block.md) |

---

## Decision

**D1 — Thuần client, không opcode mới.** `/chuyentiep <tên>` (hoặc `/forward`) lấy lá thư ĐANG CHỌN trong hòm thư, dựng
bản nháp `Fwd: <tiêu đề>` (cắt vừa 64) + nội dung gốc (cắt vừa 1024), vàng = 0, không đính kèm, rồi gửi bằng ĐÚNG lệnh
gửi thư thường (`SendMailCommand`). Mọi luật (tem thư, chặn người lạ, hộp đầy, kiểm chữ) vẫn do server quyết; kết quả hiện
như mọi lần gửi thư.

**D2 — Lệch mô tả lúc hỏi:** mô tả ghi "qua đường gửi thư thường" — đúng như vậy, nhưng làm ở client vì client đã có nội
dung thư, không cần server đọc lại.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client dựng `SendMailCommand` từ thư đang chọn; `/chuyentiep` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `MailForwardCoordinator`, `MailCoordinator.selectedMail`.
2. ✅ Test: `Lot46ClientTest.forwardDraftPrefixesAndTrims`.
3. ⏳ Thuần client — probe headless không chạy lệnh gạch chéo/không vẽ UI; phủ bằng test đơn vị + biên dịch, cần kiểm tay trên client thật.
