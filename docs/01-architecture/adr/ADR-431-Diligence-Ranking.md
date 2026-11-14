# ADR-431: Xếp hạng chăm chỉ hôm nay

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Xếp hạng chăm chỉ hôm nay" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: /topchamchi: top 10 người online lâu nhất hôm nay). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-096](ADR-096-Online-Time-Rewards.md) · [ADR-391](ADR-391-Player-Kill-Ranking.md) · [ADR-392](ADR-392-Playtime-Ranking.md) |

---

## Decision

**D1 — Nguồn:** bảng có sẵn `online_time(character_id, day, seconds)` (V86, ADR-096) — không migration. Top 10 theo `seconds` của ngày server hôm nay (`NewPlayersFacade.startOfDay`), hoà thì theo tên. Số đã ghi là số đã cộng dồn khi rời game/nhịp lưu; phiên đang mở của chính người hỏi KHÔNG cộng thêm (bảng chung cho mọi người, tránh lệch).

**D2 — Giao thức:** `GetDiligenceRankingRequest{}` / `GetDiligenceRankingResponse{entries{name, seconds}}` **1214/1215**. Client `/topchamchi` (`/topdiligent`) in phút.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Không migration; 1214/1215 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `OnlineStatsPort/Facade` (feature-checkin), `PostgresOnlineStatsAdapter`, `Lot63Commands.diligenceRanking`, `RankingMoreCoordinator`.
2. ✅ Test: `Lot63IntegrationTest.ranksOnlineTimeOfDayAndListsDays`; kiểm sống.
3. Kiểm sống 2026-11-14 (ĐạoHữu01): `topDiligent` → `ĐạoHữu01:509 BaoDo01:445 PhuTanThu:60` — đúng thứ tự giây trong `online_time` hôm nay.
