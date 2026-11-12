# ADR-299: Khoảng cách tới mục tiêu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Khoảng cách tới mục tiêu" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: hiện số mét tới mục tiêu đang nhắm trên HUD mục tiêu). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) |

---

## Decision

**D1 — Thuần client.** Khung mục tiêu thêm một dòng "N m" (làm tròn tới mét) — khoảng cách mặt phẳng giữa nhân vật và
mục tiêu (quái hoặc nhân vật). Tra vị trí bằng khoá đã đóng hộp sẵn (`CombatHudController.targetPointPerFrame`) nên đường
mỗi khung không cấp phát; chữ chỉ đổi khi số mét đổi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng khoảng cách trong `HudTargetFrame` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `TargetHudSync`, `CombatHud.setTargetDistance`, `HudTargetFrame.setDistance`.
2. ✅ Test: `Lot46ClientTest.distanceRoundsToMeters`.
3. ⏳ Kiểm sống gameProbe.
