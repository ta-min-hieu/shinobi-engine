# ADR-261: Sổ ghi chép cá nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Sổ ghi chép cá nhân" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: /so them <nội dung>, /so xem, /so xoa <số> — ghi chú riêng lưu trên máy). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-173](ADR-173-Friend-Notes.md) (ghi chú bạn bè — lưu server) · [ADR-229](ADR-229-Chat-Channel-Filter.md) (cùng chỗ lưu Preferences) |

---

## Decision

**D1 — Thuần client, lưu Preferences** (không gửi server — riêng tư, không tốn DB). `/so them <nội dung>` (≤ 120 ký tự),
`/so` hoặc `/so xem` liệt kê có số, `/so xoa <số>`; tối đa 20 dòng. Đổi máy là không mang theo — chấp nhận (khác ghi
chú bạn bè ADR-173 lưu server).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Preferences, 20 dòng × 120 ký tự; `/so` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `NotebookCoordinator`.
2. ✅ Test: `Lot41bClientTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
