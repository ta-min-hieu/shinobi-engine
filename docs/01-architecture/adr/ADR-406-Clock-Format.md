# ADR-406: Giờ dạng 12h/24h

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Giờ dạng 12h/24h" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: /dinhdanggio 12|24: đổi cách hiện giờ của các đồng hồ HUD (giờ máy, giờ server)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-372](ADR-372-Local-Clock-Hud.md) · [ADR-260](ADR-260-Server-Clock.md) |

---

## Decision

**D1:** `/dinhdanggio 12|24` (`/clockformat`) đổi định dạng giờ của đồng hồ giờ máy trên HUD (ADR-372), `/giomay` và `/gio` (ADR-260): 24h = `HH:MM`, 12h = `h:MM AM/PM`. Nhớ trên máy, mặc định 24h.

**D2:** không đổi protocol; dấu thời gian trong các danh sách (thư, nhật ký…) giữ nguyên.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, cờ 12/24h dùng chung | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `ClockFormat`, `HudInfoLines.appendHourMinute`, `ChatHudExtrasCoordinator`, `ServerClockCoordinator`, `ClientToolsCoordinator`.
2. ✅ Test: `Lot59ClientTest.formatsTwelveHourClock`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
