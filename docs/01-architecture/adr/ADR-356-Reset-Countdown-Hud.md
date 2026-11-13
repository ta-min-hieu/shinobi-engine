# ADR-356: Đếm ngược reset trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Đếm ngược reset trên HUD" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: nhãn nhỏ trên HUD đếm thời gian tới lần reset ngày kế tiếp; /demreset bật-tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-200](ADR-200-Reset-Timers.md) · [ADR-332](ADR-332-Session-Clock-Hud.md) |

---

## Decision

**D1 — Mốc:** 00:00 giờ Việt Nam kế tiếp — đúng mốc reset ngày của server (`DailyReset.ZONE`); tính trên máy người chơi để không hỏi server mỗi giây. Bảng đủ các mốc (ngày, tuần) vẫn là `/lammoi` (ADR-200, số do server tính).

**D2 — Nhãn** "Reset ngày sau: H:MM:SS" cùng cụm góc dưới trái với đồng hồ phiên (ADR-332), đổi chữ khi sang giây, không cấp phát. `/demreset` (`/resettimer`), mặc định tắt, nhớ trên máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đếm tới 00:00 giờ Việt Nam trên máy, nhãn HUD không cấp phát; `/demreset` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `ResetCountdownCoordinator`, `HudInfoLines` (dòng reset), `CombatHud.useResetCountdown`/`setResetCountdownVisible`.
2. ✅ Test: `Lot53ClientTest.nextResetIsVietnamMidnight`.
3. ⏳ Thuần client — cần chạy client thật để nhìn nhãn.
