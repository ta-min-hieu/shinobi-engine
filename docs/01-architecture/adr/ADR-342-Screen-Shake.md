# ADR-342: Rung màn hình khi trúng đòn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Rung màn hình khi trúng đòn" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: màn hình rung nhẹ khi nhận đòn mất nhiều máu (≥10% máu tối đa); có công tắc tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) · [ADR-243](ADR-243-Low-Health-Warning.md) |

---

## Decision

**D1 — Khi nào:** một đòn đánh/kỹ năng trúng CHÍNH mình với sát thương ≥ 10% máu tối đa (máu tối đa từ HUD) ⇒ camera thế giới 3D rung 0,25 giây, biên độ 0,15 đơn vị, tắt dần.

**D2 — Không cấp phát:** `WorldModelRenderer.shake` chỉ đặt ba số; độ lệch tính trong `follow` mỗi khung bằng `System.nanoTime` và sin/cos (CLAUDE.md §6). Chỉ lệch camera, không đụng vị trí nhân vật hay logic.

**D3 — Công tắc:** `/rung` (`/shake`), mặc định bật, nhớ trên máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đòn ≥ 10% máu tối đa ⇒ camera rung 0,25 giây, không cấp phát; `/rung`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `ScreenShakeCoordinator`, `WorldModelRenderer.shake`/`applyShake`.
2. ✅ Test: `Lot51ClientTest.heavyHitThreshold`.
3. ⏳ Thuần client — cần chạy client thật để nhìn rung.
