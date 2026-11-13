# ADR-308: Thu phóng giao diện

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Thu phóng giao diện" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: /giaodien 80–150%: phóng to/thu nhỏ toàn bộ HUD và cửa sổ, nhớ lựa chọn). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-294](ADR-294-Chat-Font-Size.md) |

---

## Decision

**D1 — Thuần client.** `/giaodien <80-150>` (hoặc `/uiscale`): mọi cửa sổ đăng ký trong `PanelHub` (HUD, khung chat,
mọi bảng) đổi `ScreenViewport.unitsPerPixel = 1/scale` rồi dựng lại theo kích thước cửa sổ; nhớ trong Preferences
`ui.scale`. Thế giới 3D không đổi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `PanelHub.setUiScale` qua `ScreenViewport`; `/giaodien`; Preferences | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `UiScaleCoordinator`, `PanelHub.setUiScale`.
2. ✅ Test: `Lot47ClientTest.parsesUiScalePercent`.
3. ⏳ Kiểm sống gameProbe.
