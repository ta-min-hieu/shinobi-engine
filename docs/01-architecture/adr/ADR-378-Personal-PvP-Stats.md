# ADR-378: Thống kê PvP cá nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Thống kê PvP cá nhân" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: /thongke thêm số lần hạ người chơi và số lần bị người chơi hạ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-223](ADR-223-Personal-Stats.md) · [ADR-337](ADR-337-Death-History.md) |

---

## Decision

**D1 — Ghi:** hai cột MỚI `player_kills`, `deaths_by_player` trong `character_lifetime_stats` (V181, mặc định 0). Ghi ở `DeathLogHooks` (ADR-337) — cùng chỗ đã biết kẻ hạ là người chơi — trên Virtual Thread, upsert hai dòng (kẻ hạ +1, người bị hạ +1); tự hạ mình không tính. Đấu trường Luận Kiếm vẫn có thắng/thua riêng (ADR-223).

**D2 — Dây:** CHỈ THÊM `GetPersonalStatsResponse.player_kills = 8`, `deaths_by_player = 9`; handler bọc `personalStats` sẵn có bằng `withPvpStats`. Client nối thêm một câu "Đối địch: hạ X tu sĩ, bị tu sĩ hạ Y lần" vào `/thongke`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `player_kills`/`deaths_by_player` (V181), field 8/9 chỉ thêm | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `PvpStatsFacade`, `PostgresPvpStatsAdapter`, `DeathLogHooks` (Deps thêm `pvp`), `Lot56Commands.withPvpStats`, `PersonalStatsCoordinator`.
2. ✅ Test: `Lot56IntegrationTest.pvpStatsCountBothSides`; kiểm sống (đọc thống kê).
3. ✅ Kiểm sống 2026-11-14 (gameProbe `pvpStats:`): `/thongke` trả `hạ=0 bịHạ=0` qua đường bọc `withPvpStats` (chưa có trận PvP thật trong dữ liệu thử; đường ghi có test Postgres).
