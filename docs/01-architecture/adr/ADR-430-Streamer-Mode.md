# ADR-430: Chế độ streamer

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Chế độ streamer" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: /streamer: khung chat ẩn tên người lạ (hiện "Tu sĩ #1"…) để quay/phát sóng không lộ tên). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-364](ADR-364-Friend-Chat-Colour.md) · [ADR-315](ADR-315-Chat-Bubbles.md) |

---

## Decision

**D1:** khi bật, tên người gửi KHÔNG phải mình và KHÔNG phải bạn bè được thay bằng bí danh ổn định trong phiên ("Tu sĩ #1", "#2"… theo thứ tự xuất hiện); chỉ đổi phần hiển thị trong khung chat (bộ đổi tin nối tiếp ADR-367), trả lời nhanh `/r` vẫn dùng tên thật. Tắt rồi bật lại thì đánh số lại.

**D2:** `/streamer`, nhớ trên máy, mặc định tắt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, bí danh ổn định trong phiên | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `ChatLensCoordinator.anonymize`.
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
