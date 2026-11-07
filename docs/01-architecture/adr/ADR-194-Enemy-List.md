# ADR-194: Danh sách cừu nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Danh sách cừu nhân" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: tự ghi lại ai đã giết mình ở vùng PvP (Hoang Vực), xem danh sách và họ đang online hay không, rồi xoá dần). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) (dải PvP Hoang Vực) · [ADR-142](ADR-142-Killing-Intent-And-Outlaws.md) (sát khí — cùng điểm móc) |

---

## Decision

**D1 — Khi nào ghi**: người chơi bị **người chơi khác** hạ ở world chính, và vị trí cuối của người bị hạ nằm trong **dải PvP
tự do** (cùng phán quyết `KillingIntentHooks.inPvpZone` mà sát khí dùng). Ghi CẢ khi người hạ là Ma Đầu hay đang bang
chiến — với người bị hạ, đó vẫn là kẻ đã hạ mình. Đấu trường / Luận Kiếm / hầm ngục không ghi (không phải world chính
hoặc đã đồng ý đấu).

**D2 — Lưu**: V152 bảng `pvp_enemy (character_id, enemy_character_id, kill_count, last_killed_at)`; lần hạ lặp lại cộng
`kill_count`. Giữ **20** người gần nhất (`EnemyListFacade.MAX_ENTRIES`) — cắt trong cùng transaction lúc ghi.

**D3 — Luồng**: tick thread chỉ lọc bằng RAM; lần ghi DB đẩy sang Virtual Thread (`EnemyHooks`, cùng khuôn sát khí).

**D4 — Giao thức, chỉ thêm**: `ListEnemiesRequest`/`ListEnemiesResponse{repeated EnemyView{name, kill_count,
last_killed_epoch_seconds, online}}` 1619/1620; `RemoveEnemyCommand{name}`/`RemoveEnemyResponse{removed, name}` 1621/1622.
`online` tra ở gateway lúc trả lời.

**D5 — Client**: lệnh chat `/cuunhan` (hoặc `/enemies`) in danh sách vào khung chat; `/cuunhan xoa <tên>` xoá một dòng.
Chưa có cửa sổ riêng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ghi kẻ hạ mình trong dải PvP, `pvp_enemy` (V152) trần 20, 1619–1622, `/cuunhan` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): V152, `EnemyListPort`/`EnemyListFacade` (feature-pvp), `PostgresEnemyListAdapter`,
   `EnemyHooks`, `Lot33Commands.listEnemies/removeEnemy`, `EnemyListCoordinator`.
2. ✅ Test: `EnemyListIntegrationTest` (Postgres thật: cộng dồn, thứ tự, trần, xoá), `EnemyListCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (PvP thật, server thật, V152): đặt ĐạoHữu01 và BaoDo01 ở x=320 (dải
   `wasteland_deep`); ĐạoHữu01 hạ BaoDo01 sau 5 đòn ⇒ `pvp_enemy` có dòng (BaoDo01 ← ĐạoHữu01, 1 lần) cùng lúc sát khí +1;
   BaoDo01 `enemies` ⇒ `ĐạoHữu01 x1 online=false`; `unenemy:ĐạoHữu01` ⇒ `removed=true`, lần hai `removed=false`, danh
   sách rỗng. Vị trí và sát khí của dữ liệu thử đã trả về như cũ.
4. ✅ Kèm theo: SpotBugs `RV_RETURN_VALUE_IGNORED` có sẵn từ lô 28 ở `HonorFacade.onDuelSettled` (bỏ qua điểm thực cộng) —
   nay trả tổng điểm thực cộng.
5. Chờ chủ dự án: trần 20 người là số đề xuất.
