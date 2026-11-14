# ADR-409: Bạn chung

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Bạn chung" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: /banchung <tên>: liệt kê những bạn bè mà mình và người đó cùng có). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-211](ADR-211-Friend-Suggestions.md) · [ADR-258](ADR-258-Friend-Ranking.md) |

---

## Decision

**D1:** giao danh sách bạn bè của mình và của người tên đó, trả tên các bạn chung theo thứ tự chữ. Không cần là bạn với người đó; không lộ gì ngoài những người MÌNH cũng đã là bạn.

**D2 — Dây:** `GetMutualFriendsRequest{target_name}` / `GetMutualFriendsResponse{found, target_name, names}` **1162/1163**. Client `/banchung <tên>` (`/mutual`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 1162/1163, giao hai danh sách bạn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `Lot60Commands.mutualFriends`, `LookupToolsCoordinator`.
2. ✅ Test: kiểm sống.
3. ✅ Kiểm sống 2026-11-14: `mutual:BaoDo01` ⇒ `thấy=true chung=[]` (dữ liệu thử chưa có bạn chung); tên không tồn tại ⇒ `thấy=false`.
