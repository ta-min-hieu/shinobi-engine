# ADR-371: Nhớ kênh chat cuối

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Nhớ kênh chat cuối" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: kênh chat đang chọn (thế giới/khu vực/bang...) được nhớ qua lần vào game sau). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-222](ADR-222-Trade-Chat-Channel.md) · [ADR-229](ADR-229-Chat-Channel-Filter.md) |

---

## Decision

**D1 — Nhớ:** mỗi lần bấm đổi kênh gửi, số kênh được lưu trong Preferences; vào game thì chọn lại kênh đó nếu nó có trong vòng xoay (thế giới, giao dịch, khu vực; nhóm chỉ khi đang trong nhóm) — kênh khác bị bỏ qua, giữ mặc định thế giới.

**D2 — Thuần client**, không công tắc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lưu kênh gửi trong Preferences, chọn lại nếu hợp lệ; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `ChatController.selectChannel`, `ChatCoordinator.onChannelChanged`/`restoreChannel`, `ChatHudExtrasCoordinator`.
2. ✅ Test: `Lot55ClientTest.restoresOnlyRotatingChannels`.
3. ⏳ Thuần client — cần chạy client thật.
