# ADR-316: Ping trên HUD (bật/tắt + màu)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "/pinghud bật-tắt + màu" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: thay cho "Ping trên HUD" vốn đã có nhãn độ trễ: thêm /pinghud ẩn/hiện và tô màu theo chất lượng). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-269](ADR-269-Ping-Command.md) |

---

## Decision

**Bối cảnh (ghi thật):** HUD vốn đã luôn hiện độ trễ (cột trên-phải, đo từ heartbeat). Chủ dự án chọn phần thêm.

**D1 — Thuần client.** Nhãn độ trễ tô màu xanh < 100 ms, vàng < 250 ms, đỏ ≥ 250 ms (cùng ngưỡng `/ping`); `/pinghud`
ẩn/hiện, nhớ trong Preferences `hud.ping` (mặc định hiện).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Màu theo ngưỡng `/ping`; `/pinghud` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `PingHudCoordinator`, `HudBanners.latencyColor`/`setLatencyVisible`, `CombatHud.setLatencyVisible`.
2. ✅ Test: `Lot48ClientTest.latencyColourFollowsPingThresholds`.
3. ⏳ Kiểm sống gameProbe.
