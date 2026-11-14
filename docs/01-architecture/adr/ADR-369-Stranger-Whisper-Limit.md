# ADR-369: Chống spam tin riêng người lạ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Chống spam tin riêng người lạ" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: giới hạn số người lạ (không phải bạn bè) một người có thể nhắn riêng trong 10 phút). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-091](ADR-091-Block-List.md) · [ADR-237](ADR-237-Block-Stranger-Invites.md) |

---

## Decision

**D1 — Luật:** mỗi người gửi nhắn riêng tối đa 5 người lạ KHÁC NHAU trong 10 phút; nhắn tiếp người đã có trong danh sách không tính thêm; bạn bè không bị giới hạn. Vượt ⇒ outcome mới `STRANGER_LIMIT = 17` (CHỈ THÊM). Kiểm sau chặn/riêng tư (ADR-091/237), trước khi kiểm duyệt nội dung.

**D2 — Trạng thái:** chỉ trong RAM (`StrangerWhisperLimiter`), đồng bộ bằng `synchronized`; thoát game KHÔNG xoá (tránh đăng nhập lại để lách), khởi động lại server thì mất — chấp nhận được.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 5 người lạ khác nhau / 10 phút, bạn bè miễn; RAM; outcome `STRANGER_LIMIT` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `StrangerWhisperLimiter`, `Lot55Commands.mayWhisper`, `GatewayChannelHandler.resolvePrivateTarget`.
2. ✅ Test: `Lot55ServerTest.limitsDistinctStrangersPerWindow`.
3. LIVE369
