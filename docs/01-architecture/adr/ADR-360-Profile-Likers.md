# ADR-360: Ai đã thích hồ sơ mình

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Ai đã thích hồ sơ mình" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: /aithich: 10 người gần nhất đã thích hồ sơ của bạn (hiện chỉ có tổng số lượt)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-298](ADR-298-Profile-Likes.md) · [ADR-305](ADR-305-Like-Ranking.md) |

---

## Decision

**D1 — Nguồn:** bảng `profile_like` (ADR-298, mỗi lượt một ngày). Gom theo người thích, lấy ngày gần nhất, mới trước, 10 người; kèm tổng lượt như trước. Chỉ mục `(target_id, liked_on DESC)` V180.

**D2 — Dây:** `GetProfileLikersRequest{}` / `GetProfileLikersResponse{repeated ProfileLiker{name, liked_on_epoch_day}, total}` **291/292**. Client `/aithich` (`/likers`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Gom `profile_like` theo người thích, 10 người mới nhất; 291/292 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `ProfileVisitPort.recentLikers`, `PostgresProfileVisitAdapter`, `Lot54Commands.profileLikers`, `ProfileVisitsCoordinator`.
2. ✅ Test: `Lot54IntegrationTest.likersNewestDayFirst`.
3. ✅ Kiểm sống gameProbe 2026-11-14: thêm một lượt thích thử BaoDo01 → ĐạoHữu01; `likers` ⇒ tổng=1 BaoDo01. Đã xoá lượt thử.
