# ADR-353: Tìm người chơi theo tên

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Tìm người chơi theo tên" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: /timnguoi <vài chữ>: tối đa 10 nhân vật có tên chứa chuỗi đó, kèm cấp và đang online không). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-211](ADR-211-Friend-Suggestions.md) |

---

## Decision

**D1 — Khớp:** chuỗi con, không phân biệt hoa thường; `% _ \` người gõ được thoát (khớp nguyên văn). Tên bắt đầu bằng chuỗi lên trước, rồi tên ngắn hơn. 2–16 ký tự, không ký tự điều khiển; sai ⇒ danh sách rỗng. Tối đa 10 người. Bảng nhân vật nhỏ ở quy mô hiện tại — không thêm chỉ mục trigram (cần extension Postgres riêng).

**D2 — Dây:** `SearchPlayersRequest{query}` / `SearchPlayersResponse{repeated PlayerHit{name, level, online}}` **289/290**. Client `/timnguoi <chữ>` (`/findplayer`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | ILIKE chuỗi con có thoát ký tự, 2–16 ký tự, 10 người; 289/290 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `PlayerLookupPort.search`/`PlayerLookupFacade.validQuery`, `Lot53Commands.searchPlayers`, `PlayerLookupCoordinator`.
2. ✅ Test: `Lot53IntegrationTest.searchMatchesSubstringAndEscapesWildcards`, `Lot53ServerTest.validatesSearchQuery`.
3. LIVE353
