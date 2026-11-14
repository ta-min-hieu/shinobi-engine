# ADR-432: Thời gian online 7 ngày

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Thời gian online 7 ngày" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: /online7: số phút online từng ngày trong 7 ngày gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-096](ADR-096-Online-Time-Rewards.md) · [ADR-431](ADR-431-Diligence-Ranking.md) |

---

## Decision

**D1 — Nguồn:** `online_time` của chính mình, 7 ngày tính tới hôm nay (ngày thiếu = 0). Ngày hôm nay cộng thêm giây của phiên đang mở (từ `max(lúc vào world, đầu ngày)` tới bây giờ) để con số khớp cảm nhận.

**D2 — Giao thức:** `GetOnlineWeekRequest{}` / `GetOnlineWeekResponse{days{day_epoch_days, seconds}}` **1216/1217**, cũ trước. Client `/online7` (`/onlineweek`) in một dòng `dd/MM N'` kèm tổng phút.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Không migration; 1216/1217 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `OnlineStatsFacade.lastDays`, `Lot63Commands.onlineWeek/liveSecondsToday`, `RankingMoreCoordinator.describeWeek`.
2. ✅ Test: `Lot63ServerTest.countsLiveSecondsOnlyFromStartOfDay`, `Lot63ClientTest.describesOnlineWeekWithTotal`; kiểm sống.
3. Kiểm sống 2026-11-14: `onlineWeek` → 7 ngày 08/11…14/11 (ngày thiếu = 0, hôm nay 509 giây), khớp bảng.
