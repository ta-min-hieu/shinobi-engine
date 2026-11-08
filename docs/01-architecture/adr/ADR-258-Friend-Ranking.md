# ADR-258: Xếp hạng bạn bè

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Xếp hạng bạn bè" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: bảng Lực Chiến / cấp chỉ trong danh sách bạn bè của mình). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-056](ADR-056-Character-Stat-Sheet-And-Combat-Power.md) (Lực Chiến) · [ADR-057](ADR-057-Leaderboards.md) (bộ đệm bảng) |

---

## Decision

**D1 — Lọc bảng Lực Chiến đã đệm.** `GetFriendRankingRequest{}` / `GetFriendRankingResponse{repeated FriendRankEntry{character_id,
name, level, combat_power, is_self}}` 1155/1156 (dải Friend). Lấy đúng bảng Lực Chiến của Bia Anh Danh (bộ đệm 60
giây, ADR-057) rồi giữ lại mình + bạn bè, xếp giảm dần — không tính Lực Chiến lần thứ hai, không lộ gì ngoài những gì
bảng công khai đã có. Client `/xephangban` (hoặc `/friendrank`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 1155/1156 lọc bảng Lực Chiến đã đệm theo mình + bạn bè; `/xephangban` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `Lot41Commands.friendRanking`, `FriendRankingCoordinator`.
2. ✅ Test: kiểm sống.
3. ✅ Kiểm sống gameProbe 2026-11-08: ĐạoHữu01 ⇒ `ĐạoHữu01:1652(mình) BaoDo01:630`; BaoDo01 thấy cùng bảng, đánh dấu mình đúng.
