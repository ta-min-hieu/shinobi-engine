# ADR-397: Lọc chat chỉ bạn bè

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Lọc chat chỉ bạn bè" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: /chatbanbe: khung chat chỉ hiện tin của bạn bè (và tin hệ thống)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-229](ADR-229-Chat-Channel-Filter.md) · [ADR-364](ADR-364-Friend-Chat-Colour.md) |

---

## Decision

**D1:** khi bật, ẩn tin kênh CÔNG CỘNG (thế giới, khu vực, giao dịch) của người không phải bạn bè; tin bang, nhóm, tin riêng, tin hệ thống và tin của chính mình luôn hiện (đó là kênh quan hệ, không phải người lạ). Nguồn "là bạn" dùng chung với tô màu bạn bè (ADR-364).

**D2:** `/chatbanbe` (`/friendchat`) bật/tắt, nhớ trên máy. Lọc chỉ ở client (tin vẫn tới); thêm `ChatCoordinator.addEventFilter` nối HOẶC với chặn người / lọc từ khoá.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, chỉ lọc kênh công cộng | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `ChatViewCoordinator.hides`, `ChatCoordinator.addEventFilter`.
2. ✅ Test: `Lot58ClientTest.friendFilterOnlyTouchesPublicChannels`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
