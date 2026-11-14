# ADR-438: Ẩn/hiện mọi dòng HUD phụ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Ẩn/hiện mọi dòng HUD phụ" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: /hudphu ẩn hoặc hiện cả cụm dòng thông tin phụ trên HUD một lần). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-318](ADR-318-Minimal-Hud.md) · [ADR-411](ADR-411-Next-Event-Hud.md) |

---

## Decision

**D1 — Phạm vi:** cả nhóm `HudInfoLines` (đồng hồ, toạ độ, DPS, linh thạch phiên, nhắc việc…) — `CombatHud.setInfoLinesVisible` ẩn cả group, không đổi công tắc riêng từng dòng (bật lại thì mỗi dòng giữ trạng thái cũ).

**D2 — Lệnh:** `/hudphu` (`/infohud`) đảo trạng thái, nhớ trong Preferences, áp ngay khi vào game. Chỉ client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Chỉ client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `CombatHud.setInfoLinesVisible`, `HudPolishCoordinator.useInfoLines`.
2. ✅ Test: Biên dịch + kiểm bằng mắt.
3. Chỉ client — biên dịch + check client xanh.
