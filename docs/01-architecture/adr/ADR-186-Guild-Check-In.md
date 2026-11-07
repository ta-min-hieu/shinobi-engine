# ADR-186: Điểm danh bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Điểm danh bang" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: mỗi ngày thành viên điểm danh bang một lần: +cống hiến cho mình, +kinh nghiệm cho bang). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-060](ADR-060-Daily-Check-In.md) (điểm danh cá nhân — cùng ngày giờ Việt Nam) · [ADR-068](ADR-068-Guild-Level-And-Contribution.md) (cống hiến, kinh nghiệm bang) |

---

## Decision

**D1 — Một lần mỗi NHÂN VẬT mỗi ngày** (giờ Việt Nam, như ADR-060): khoá chính `(character_id, day)` của
`guild_checkin` (V147) — rời bang vào bang khác trong ngày KHÔNG điểm danh lại được (chặn đi vòng nhiều bang).

**D2 — Thưởng**: **+100 cống hiến** cho người điểm danh, **+100 kinh nghiệm** cho bang (PLACEHOLDER). Không tốn gì, không
vàng. Kinh nghiệm bang đi qua ĐÚNG hàm cộng của góp bang (`addGuildExp`, khoá dòng bang, tính lại cấp) — bang lên cấp ⇒
dòng nhật ký `LEVEL_UP` và cả bang nhận 720 `LEVEL_UP` như góp bang. Lượt điểm danh không ghi nhật ký bang (50 dòng/ngày
sẽ đẩy trôi mọi việc khác).

**D3 — Giao thức, chỉ thêm**: `GuildCheckInCommand{}` / `GuildCheckInResponse{outcome, contribution, guild_exp,
today_count}` 755/756 — `today_count` = số người của bang đã điểm danh hôm nay.

**D4 — Client**: lệnh chat `/diemdanhbang` (hoặc `/guildcheckin`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Một lần / nhân vật / ngày (V147), +100 cống hiến, +100 kinh nghiệm bang qua `addGuildExp`, 755/756 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): V147, `GuildCheckInPort`/`GuildCheckInFacade`, `PostgresGuildCheckInAdapter`,
   `Lot32Commands.guildCheckIn` 755/756, `GuildCheckInCoordinator` `/diemdanhbang`.
2. ✅ Test: `GuildCheckInIntegrationTest` (Postgres thật), `GuildCheckInCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `guildCheckIn`): ĐạoHữu01 ⇒ `OK` +100/+100, hôm nay 1; lần hai ⇒
   `ALREADY_CHECKED_IN`; BaoDo01 ⇒ `OK`, hôm nay 2; DB: cống hiến 100 / 2100, kinh nghiệm bang 2000 → 2200.
4. Chờ chủ dự án: mức thưởng 100/100; có muốn nút điểm danh trong cửa sổ bang không.
