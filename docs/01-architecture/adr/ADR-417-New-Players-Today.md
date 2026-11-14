# ADR-417: Đếm tân thủ hôm nay

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Đếm tân thủ hôm nay" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: /tanthu: số nhân vật mới tạo hôm nay và tên vài người gần nhất để chào đón). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-227](ADR-227-Starter-Gift.md) |

---

## Decision

**D1:** đếm nhân vật có `created_at` từ 00:00 giờ Việt Nam hôm nay (cùng mốc reset ngày), kèm tối đa **5** tên mới nhất. Không bảng mới.

**D2 — Dây:** `GetNewPlayersRequest{}` / `GetNewPlayersResponse{count, newest}` **1210/1211**. Client `/tanthu` (`/newplayers`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 1210/1211, ngày giờ Việt Nam | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `NewPlayersPort/Facade`, `PostgresNewPlayersAdapter`, `Lot61Commands.newPlayers`, `FriendDigestCoordinator`.
2. ✅ Test: `Lot61IntegrationTest.countsAndListsNewestSince`, `Lot61ServerTest.dayStartsAtVietnamMidnight`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: `newPlayers:` ⇒ `số=0` (dữ liệu thử không có nhân vật tạo hôm nay theo giờ Việt Nam); đường đếm + tên có test Postgres.
