# ADR-401: Xem lại chat bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Xem lại chat bang" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: /lichsubang: xem 30 tin chat bang gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-304](ADR-304-Guild-Chat-Backlog.md) |

---

## Decision

**D1:** đọc bộ nhớ tin bang trong RAM (ADR-304, giữ 50 tin/bang), phát lại tối đa **30** tin gần nhất cho riêng người hỏi, mỗi tin kèm giờ gửi `[HH:mm UTC]` như tin bang lúc vắng. Khởi động lại server thì bộ nhớ rỗng (như ADR-304).

**D2 — Dây:** `GetGuildChatHistoryRequest{}` / `GetGuildChatHistoryResponse{in_guild, count}` **797/798**; các tin đi trước bằng `ChatMessageEvent` kênh bang. Client `/lichsubang` (`/guildhistory`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 797/798, phát lại ≤ 30 tin từ RAM | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `GuildChatBacklog.recent`, `Lot59Commands.guildChatHistory`, `GuildToolsCoordinator`.
2. ✅ Test: `Lot59ServerTest.recentKeepsNewestInOrder`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: BaoDo01 chat kênh bang rồi ĐạoHữu01 `guildHistory:` ⇒ `bang=true tin=1`.
