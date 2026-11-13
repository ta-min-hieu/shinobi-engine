# ADR-329: Người vừa chơi cùng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Người vừa chơi cùng" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: server ghi lại 10 người gần nhất từng chung nhóm với bạn; xem danh sách để kết bạn/mời lại). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) · [ADR-211](ADR-211-Friend-Suggestions.md) |

---

## Decision

**D1 — Ghi khi vào nhóm:** mỗi khi một người vào nhóm (đường báo đổi thành viên ADR-059), người đó và MỌI thành viên khác ghi tên nhau vào bảng `recent_player(character_id, other_id, met_at)` (V176); đã có thì làm mới `met_at`. Mỗi nhân vật chỉ giữ 10 người mới nhất (cắt lúc ghi).

**D2 — Xem:** tên, cấp, đang online không, lần gần nhất; mới trước. Việc phụ: lỗi ghi không làm hỏng lệnh vào nhóm.

**D3 — Dây:** `GetRecentPlayersRequest{}` / `GetRecentPlayersResponse{repeated RecentPlayer{name, level, online, met_at_epoch_seconds}}` **1347/1348**. Client `/vuachoi` (`/recent`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `recent_player` (V176) ghi lúc vào nhóm, giữ 10; 1347/1348 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `RecentPlayerPort`/`Facade` (feature-party), `PostgresRecentPlayerAdapter`, `RecentPlayerHooks` (nối trong `MembershipNotices`), `Lot50Commands.recentPlayers`, `RecentPlayersCoordinator`.
2. ✅ Test: `Lot50IntegrationTest.recentPlayersKeepNewestOnly`.
3. ✅ Kiểm sống gameProbe 2026-11-13 (hai probe): ĐạoHữu01 rời nhóm rồi nhận lời mời của BaoDo01 ⇒ `recent_player` có 2 dòng hai chiều; `recentPlayers` của BaoDo01 ⇒ "ĐạoHữu01". Đã xoá dòng thử, trả vai trò nhóm về như cũ.
