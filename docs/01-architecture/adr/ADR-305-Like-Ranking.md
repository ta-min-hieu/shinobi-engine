# ADR-305: Xếp hạng lượt thích

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Xếp hạng lượt thích" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: /topthich: top người chơi được thích hồ sơ nhiều nhất (dựa trên lượt thích lô 46)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-298](ADR-298-Profile-Likes.md) |

---

## Decision

**D1 —** Đếm `profile_like` (ADR-298) theo người được thích, top 10, nhiều trước (hoà ⇒ theo tên).
`ProfileLikePort.top`. Không migration (chỉ mục `idx_profile_like_target` của V173).

**D2 — Dây:** `GetLikeRankingRequest{}` / `GetLikeRankingResponse{repeated LikeRank{name, likes}}` **273/274**. Client
`/topthich` (hoặc `/toplikes`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đếm `profile_like`; 273/274; `/topthich` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `ProfileLikePort.top`, `Lot47Commands.likeRanking`, `BossDamageRankingCoordinator`.
2. ✅ Test: `Lot47IntegrationTest.ranksProfileLikes`.
3. ✅ Kiểm sống gameProbe 2026-11-13 (`topLikes`): danh sách rỗng — đúng dữ liệu dev (`profile_like` đã dọn sau kiểm sống ADR-298). Truy vấn phủ bởi `Lot47IntegrationTest.ranksProfileLikes`.
