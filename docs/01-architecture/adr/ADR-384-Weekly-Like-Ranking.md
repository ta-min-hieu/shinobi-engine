# ADR-384: Xếp hạng lượt thích tuần

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Xếp hạng lượt thích tuần" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: /thichtuan: top 10 lượt thích hồ sơ trong 7 ngày gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-305](ADR-305-Like-Ranking.md) · [ADR-298](ADR-298-Profile-Likes.md) |

---

## Decision

**D1:** đếm `profile_like` có `liked_on` trong 7 ngày gần nhất (trượt, gồm hôm nay, ngày UTC như ADR-298), top 10, nhiều trước, cùng số theo tên. Không bảng mới.

**D2 — Dây:** CHỈ THÊM `GetLikeRankingRequest.weekly = 1` và `GetLikeRankingResponse.weekly = 2` (nhắc lại để client ghi tiêu đề) — không opcode mới, client cũ gửi rỗng vẫn nhận bảng cả đời. Client `/thichtuan` (`/weeklylikes`), in qua luồng `/topthich`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Field `weekly` 1/2 chỉ thêm trên cặp opcode xếp hạng thích sẵn có | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `ProfileLikePort.topSince`, `ProfileLikeFacade.topThisWeek`, `Lot57Commands.likeRanking`, `BossDamageRankingCoordinator` (tiêu đề tuần).
2. ✅ Test: `Lot57IntegrationTest.weeklyLikesCountOnlyRecentDays`, `Lot57ServerTest.allTimeRankingPassesThrough`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `likeWeek:`): phản hồi `weekly=true`, 0 dòng (dữ liệu thử chưa có lượt thích nào).
