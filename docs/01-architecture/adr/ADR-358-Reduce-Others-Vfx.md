# ADR-358: Giảm hiệu ứng kỹ năng người khác

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Giảm hiệu ứng người khác" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: tắt hiệu ứng kỹ năng (VFX) của người chơi khác cho đỡ rối và nhẹ máy; trong cài đặt hiển thị). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-143](ADR-143-Display-Settings.md) · [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) |

---

## Decision

**D1 — Phạm vi:** công tắc mới trong cài đặt hiển thị (ADR-143) "Giảm hiệu ứng kỹ năng của người khác", mặc định TẮT. Bật ⇒ không vẽ VFX của đòn kỹ năng mà mình không phải người ra đòn cũng không phải người trúng; số sát thương vẫn theo công tắc riêng (ADR-285).

**D2 — Thuần client**, một `BooleanSupplier` đọc mỗi lần có kỹ năng (không cấp phát).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Công tắc `HIDE_OTHERS_VFX`; bỏ VFX kỹ năng không dính tới mình; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `DisplaySettings.Option.HIDE_OTHERS_VFX`, `CombatFeedbackPresenter.useOthersVfxFilter`/`involvesMe`.
2. ✅ Test: công tắc theo khuôn `DisplaySettings` đã có test; phần vẽ kiểm bằng client thật.
3. ⏳ Thuần client — cần chạy client thật để nhìn.
