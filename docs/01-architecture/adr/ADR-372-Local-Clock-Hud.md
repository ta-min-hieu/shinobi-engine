# ADR-372: Đồng hồ giờ máy trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Đồng hồ giờ máy trên HUD" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: nhãn giờ hiện tại (HH:MM) của máy người chơi trên HUD; /giomay bật-tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-260](ADR-260-Server-Clock.md) · [ADR-332](ADR-332-Session-Clock-Hud.md) |

---

## Decision

**D1 — Nhãn** HH:MM theo giờ máy, cùng cụm góc dưới trái với đồng hồ phiên (ADR-332); đọc mỗi khung, chỉ đổi chữ khi sang phút, dựng vào bộ đệm có sẵn (không cấp phát). Khác đồng hồ giờ server (ADR-260, in ra chat).

**D2 — Công tắc** `/giomay` (`/localtime`), mặc định tắt, nhớ trên máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhãn HH:MM giờ máy, không cấp phát; `/giomay` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `HudInfoLines.updateLocalClock`/`appendHourMinute`, `CombatHud.useLocalClock`, `ChatHudExtrasCoordinator`.
2. ✅ Test: `Lot55HudTest.formatsHourMinute`.
3. ⏳ Thuần client — cần chạy client thật.
