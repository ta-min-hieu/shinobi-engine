# ADR-351: Xếp hạng chuỗi đăng nhập

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Xếp hạng chuỗi đăng nhập" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: /topchuoi: 10 người có chuỗi đăng nhập liên tiếp dài nhất (từ bảng của ADR-327)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-327](ADR-327-Login-Streak.md) · [ADR-057](ADR-057-Leaderboards.md) |

---

## Decision

**D1 — Nguồn:** bảng `login_streak` (ADR-327). Chỉ tính chuỗi CÒN SỐNG — ngày vào cuối là hôm nay hoặc hôm qua (giờ Việt Nam) — để chuỗi đã đứt không chiếm bảng. Xếp theo chuỗi hiện tại, hoà thì chuỗi dài nhất, rồi tên. Không thêm bảng.

**D2 — Dây:** `GetStreakRankingRequest{}` / `GetStreakRankingResponse{repeated StreakRank{name, streak}}` **285/286**. Client `/topchuoi` (`/topstreak`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Top 10 chuỗi còn sống từ `login_streak`; 285/286 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `PlayerLookupPort.topStreaks`, `PostgresPlayerLookupAdapter`, `Lot53Commands.streakRanking`, `PlayerLookupCoordinator`.
2. ✅ Test: `Lot53IntegrationTest.ranksStreaksAndReferrers`.
3. ✅ Kiểm sống gameProbe 2026-11-13: sau khi hai nhân vật vào world trong ngày, `topStreak` ⇒ BaoDo01=1 ĐạoHữu01=1. Đã xoá dòng thử.
