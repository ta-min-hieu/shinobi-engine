# ADR-437: Hẹn giờ nhắc trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Hẹn giờ nhắc trên HUD" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: Dòng HUD "Nhắc việc mm:ss" đếm tới lời nhắc /hengio gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-259](ADR-259-Reminder-Timer.md) · [ADR-411](ADR-411-Next-Event-Hud.md) |

---

## Decision

**D1 — Nguồn:** `ReminderTimerCoordinator` (ADR-259) giữ thêm mốc hết giờ của từng lời nhắc; `secondsToNext` = giây tới mốc sớm nhất, không có ⇒ -1 (dòng HUD để trống).

**D2 — Hiện:** một dòng đếm `hud.addCounterLine("hud.next_reminder", …, clock)` như các dòng lô 60. Luôn bật khi có lời nhắc; ẩn cùng `/hudphu`. Chỉ client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Chỉ client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `ReminderTimerCoordinator.secondsToNext`, `ExpansionWindows.wireLot63Display`.
2. ✅ Test: `Lot63ClientTest.secondsToNearestReminder`.
3. Chỉ client — `Lot63ClientTest` xanh.
