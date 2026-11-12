# ADR-283: Bộ đếm FPS

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Bộ đếm FPS" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: /fps bật/tắt số khung hình/giây ở góc màn hình). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) |

---

## Decision

**D1 — Thuần client.** Nhãn "FPS N" góc dưới trái HUD chiến đấu (`CombatHud`), số lấy từ
`Gdx.graphics.getFramesPerSecond()`; chỉ cập nhật chữ khi số đổi, bộ đệm `StringBuilder` tái dùng — không cấp phát mỗi
khung (Điều 31). `/fps` bật/tắt, nhớ trong Preferences. Ẩn cùng HUD ở chế độ chụp ảnh (ADR-244).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhãn FPS trong `CombatHud`, cập nhật khi đổi, Preferences; `/fps` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `FpsCoordinator`, `CombatHud.setFpsVisible`.
2. ✅ Test: không có test riêng (một nhãn + một công tắc); không cấp phát mỗi khung — rà bằng mắt.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
