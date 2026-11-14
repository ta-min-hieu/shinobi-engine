# ADR-392: Xếp hạng thời gian tu luyện

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Xếp hạng thời gian tu luyện" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: /toptuluyen: top 10 tổng thời gian chơi). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-223](ADR-223-Personal-Stats.md) · [ADR-391](ADR-391-Player-Kill-Ranking.md) |

---

## Decision

**D1:** đọc `playtime_seconds` (ADR-223 — cộng khi phiên kết thúc, nên phiên đang chơi chưa tính), top 10. Client hiện theo giờ (làm tròn xuống).

**D2:** dùng chung cặp **1206/1207** của ADR-391 với `kind = PLAYTIME`. Client `/toptuluyen` (`/topplaytime`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cùng 1206/1207, kind `PLAYTIME` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `LifetimeRankingPort.topPlaytime`, `LifetimeRankingCoordinator`.
2. ✅ Test: `Lot58IntegrationTest.ranksKillsAndPlaytime`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `lifetimeRank:playtime`): `ĐạoHữu01=832 BaoDo01=585 PhuTanThu=40 QuaTanThu=10` (giây), lớn trước.
