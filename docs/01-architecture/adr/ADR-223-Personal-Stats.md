# ADR-223: Thống kê cá nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Thống kê cá nhân" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: quái đã hạ, số lần chết, PvP thắng/thua, tổng giờ chơi). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-062](ADR-062-Achievements-And-Titles.md) (bộ đếm thành tựu) · [ADR-195](ADR-195-Death-Recap.md) (đường "bị hạ") |

---

## Decision

**D1 — Dùng lại số đã có**: quái đã hạ, nhiệm vụ đã trả lấy từ bộ đếm thành tựu (V64); thắng / thua Luận Kiếm từ
`pvp_rating`; số thành tựu đã mở từ `character_achievement`.

**D2 — Hai số mới, V165 `character_lifetime_stats`**: `deaths` (+1 mỗi lần nhân vật bị hạ — móc vào đúng đường "bị hạ"
của tóm tắt khi chết, ghi trên Virtual Thread, không chạm tick thread) và `playtime_seconds` (cộng thời lượng mỗi phiên
trong world khi rời world). Bảng thống kê cộng thêm cả phiên đang chơi. Mất kết nối đột ngột vẫn qua đường rời world
như mọi lần ngắt.

**D3 — Protocol**: `GetPersonalStatsRequest{}` / `GetPersonalStatsResponse{monsters_killed, quests_claimed, deaths,
pvp_wins, pvp_losses, achievements_unlocked, playtime_seconds}` 261/262.

**D4 — Client**: `/thongke` (hoặc `/stats`) in một dòng vào khung chat.

**Lưu ý**: số lần chết và giờ chơi đếm **từ lúc thi hành** (2026-11-08) — dữ liệu trước đó không có để bù.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Số cũ từ bộ đếm sẵn có + hai cột mới deaths/playtime (V165); 261/262; `/thongke` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): V165, `LifetimeStatsPort`/`Facade`, `PostgresLifetimeStatsAdapter`,
   `DeathRecapHooks.bindDeathCounter`, `Lot37Commands.onEnteredWorld/onLeftWorld/personalStats`,
   `PersonalStatsCoordinator`.
2. ✅ Test: `LifetimeStatsIntegrationTest`, `PersonalStatsTextTest`.
3. ✅ Kiểm sống gameProbe 2026-11-08 (V165): ĐạoHữu01 `personalStats` ⇒ `quái=8 chết=0 pvp=1/0 nhiệmVụ=0 thànhTựu=2` (khớp
   bộ đếm thành tựu + `pvp_rating`); sau một phiên ~50 giây, DB có `playtime_seconds` cộng đúng phiên đó và lần hỏi kế
   hiện tổng mới. Đường "bị hạ ⇒ +1" chỉ kiểm bằng test (probe không tự chết được).
