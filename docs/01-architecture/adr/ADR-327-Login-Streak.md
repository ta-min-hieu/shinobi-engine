# ADR-327: Chuỗi đăng nhập liên tiếp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Chuỗi đăng nhập liên tiếp" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: đếm số ngày vào game liên tiếp; mốc 3/7 ngày tặng vàng, đứt chuỗi thì về 1. Xem bằng lệnh). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-060](ADR-060-Daily-Check-In.md) · [ADR-058](ADR-058-Send-Mail-Idempotency.md) · [ADR-200](ADR-200-Reset-Timers.md) |

---

## Decision

**D1 — Ngày = ngày giờ Việt Nam** (`DailyReset.ZONE`, cùng mốc reset ngày). Bảng `login_streak(character_id, streak, best, last_day)` (V176). Lần vào world ĐẦU TIÊN trong ngày: hôm qua có vào ⇒ chuỗi +1, không ⇒ về 1. Một câu UPSERT có điều kiện `WHERE last_day < EXCLUDED.last_day` nên vào world nhiều lần / song song trong ngày chỉ tính một.

**D2 — Thưởng:** ngày thứ 3 ⇒ 500 linh thạch; mọi bội của 7 (7, 14, 21…) ⇒ 1.500 linh thạch, gửi bằng thư hệ thống với khoá chống trùng theo (nhân vật, ngày) — ADR-058. Mỗi lần chuỗi tăng có một dòng báo hệ thống. Khác điểm danh (ADR-060): điểm danh là bấm nhận theo lịch tháng, chuỗi là tự động và phạt bỏ ngày.

**D3 — Dây:** `GetLoginStreakRequest{}` / `GetLoginStreakResponse{current, best, next_reward_day, next_reward_gold}` **279/280** (`current` = 0 khi đã đứt). Client `/chuoi` (`/streak`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `login_streak` (V176), UPSERT có điều kiện, thưởng 3 ngày 500 / bội 7 ngày 1.500 qua thư; 279/280 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `LoginStreakPort`/`Facade`, `PostgresLoginStreakAdapter`, `Lot50Commands.onEnteredWorld`/`loginStreak` (gọi từ `Lot48Commands.onEnteredWorld`), `LoginStreakCoordinator`.
2. ✅ Test: `Lot50IntegrationTest.streakCountsOncePerDayAndResetsAfterGap`, `Lot50ServerTest.streakRewardsOnMilestones`/`streakKeyIsPerDay`/`streakNoticeMentionsGiftOnlyOnMilestone`.
3. ✅ Kiểm sống gameProbe 2026-11-13: vào world ⇒ dòng "Chuỗi đăng nhập: 1 ngày liên tiếp"; vào lần hai cùng ngày không tăng; `loginStreak` ⇒ chuỗi=1 dàiNhất=1 mốc=3:500. Mốc thưởng 3/7 ngày phủ bằng test. Đã xoá dòng thử.
