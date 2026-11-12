# ADR-280: Lịch sử đấu tay đôi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Lịch sử đấu tay đôi" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: /lichsudau: 10 trận đấu tay đôi gần nhất (đối thủ, thắng/thua, thời gian)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-057](ADR-057-Leaderboards.md) (Elo đấu trường) · [ADR-233](ADR-233-Enhance-History.md) (cùng khuôn đọc audit) |

---

## Decision

**D1 — Ghi audit trong CÙNG giao dịch tính Elo.** Trước đây trận đấu tay đôi chỉ để lại số thắng/thua + điểm trong
`pvp_rating`, không có dòng từng trận. Nay `PostgresPvpRatingAdapter.recordDuelResult` ghi thêm **hai** dòng audit
`DUEL_RESULT {opponentId, won, ratingAfter}` (mỗi bên một dòng) trong đúng giao dịch khoá-tính-ghi Elo — không trận nào
có điểm mà thiếu lịch sử hay ngược lại. Không migration.

**D2 — Đọc:** `GetDuelHistoryRequest{}` / `GetDuelHistoryResponse{repeated DuelHistoryEntry{opponent_name, won,
rating_after, at_epoch_seconds}}` 1204/1205 (dải PvP) — 10 trận mới nhất, tên đối thủ nối từ `character` (đã xoá ⇒
"?"). **Trận trước ADR này không có lịch sử** (không có dữ liệu để dựng lại). Client `/lichsudau` (hoặc `/duels`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Audit `DUEL_RESULT` hai bên trong giao dịch Elo; 1204/1205 trả 10 trận; `/lichsudau` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `PostgresPvpRatingAdapter` (audit), `DuelHistoryPort`/`DuelHistoryFacade`, `PostgresDuelHistoryAdapter`, `Lot44Commands.duelHistory`, `DuelHistoryCoordinator`.
2. ✅ Test: `Lot44IntegrationTest.duelResultsLeaveHistoryOnBothSides`, `PostgresPvpRatingAdapterIntegrationTest` (Elo không đổi).
3. ✅ Kiểm sống gameProbe 2026-11-12 (`duels`): danh sách rỗng, đúng kỳ vọng — dữ liệu chỉ ghi từ ADR này trở đi. Đường ghi audit + đọc lại phủ bởi `Lot44IntegrationTest` (Postgres thật).
