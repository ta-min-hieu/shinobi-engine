# ADR-434: Xếp hạng thân mật

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Xếp hạng thân mật" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: /topthanmat: top 10 cặp bạn bè có độ thân mật cao nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-084](ADR-084-Friend-Gifts.md) · [ADR-258](ADR-258-Friend-Ranking.md) |

---

## Decision

**D1 — Nguồn:** cột `friendship.intimacy` (V80, ADR-084), chỉ quan hệ `ACCEPTED` và `intimacy > 0`; top 10, hoà theo tên. Mỗi cặp hiện một lần (một dòng friendship).

**D2 — Giao thức:** `GetIntimacyRankingRequest{}` / `GetIntimacyRankingResponse{pairs{first_name, second_name, intimacy}}` **1218/1219**. Client `/topthanmat` (`/topintimacy`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Không migration; 1218/1219 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `IntimacyRankingPort/Facade` (feature-friend), `PostgresIntimacyRankingAdapter`, `Lot63Commands.intimacyRanking`, `RankingMoreCoordinator`.
2. ✅ Test: `Lot63IntegrationTest.ranksAcceptedFriendshipsByIntimacy`; kiểm sống.
3. Kiểm sống 2026-11-14: độ thân mật thật đang 0 ⇒ `cặp=` rỗng; tạm đặt `intimacy=5` cho cặp ĐạoHữu01–BaoDo01 ⇒ `ĐạoHữu01&BaoDo01:5`; đã trả lại 0.
