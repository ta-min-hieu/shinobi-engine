# ADR-230: Khoe thành tựu vào bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Khoe thành tựu vào bang" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: mở thành tựu thì tự báo một dòng vào kênh bang, có công tắc tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-062](ADR-062-Achievements-And-Titles.md) (thành tựu) · [ADR-180](ADR-180-Guild-Log.md) (nhật ký bang) |

---

## Decision

**D1 — Đúng đường mở thành tựu**: mở thành tựu mới ⇒ (nếu người đó đang ở bang và đang bật khoe) mọi thành viên bang
đang online, kể cả mình, nhận `GuildAchievementSharedEvent{character_name, achievement_name_key}` (1707); client tự
dịch tên thành tựu và hiện "[Bang] X vừa mở thành tựu "Y"!". Không ghi nhật ký bang (tránh làm loãng), không lưu tin.

**D2 — Công tắc**: V166 `character.share_achievements` (mặc định **BẬT**). `SetAchievementShareCommand{enabled,
query_only}` / `Response{enabled}` 1705/1706.

**D3 — Client**: `/khoett bat|tat` (`/shareach on|off`), không kèm gì = xem trạng thái.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Event 1707 tới cả bang khi mở thành tựu, công tắc V166 mặc định bật; 1705/1706; `/khoett` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): V166, `AchievementSharePort`/`Facade`, `PostgresAchievementShareAdapter`,
   `AchievementShareHooks`, `AchievementHooks.bindUnlockListener`, `AchievementShareCoordinator`.
2. ✅ Test: `Lot37IntegrationTest` (mặc định bật, tắt được).
3. ✅ Kiểm sống gameProbe 2026-11-08 (V166): `shareAch:?` ⇒ `khoe=true` (mặc định bật); BaoDo01 giữ online, ĐạoHữu01 (cùng
   bang, đã hạ 8 quái) hạ thêm 2 mộc nhân ⇒ mở `slayer_10` (`ACHIEVEMENT_UNLOCKED_EVENT`) và BaoDo01 nhận
   `GUILD_ACHIEVEMENT_SHARED_EVENT` (1707). Đã trả bộ đếm, thành tựu, đồ rơi và vị trí về như cũ (EXP từ hai con mộc nhân
   giữ lại).
