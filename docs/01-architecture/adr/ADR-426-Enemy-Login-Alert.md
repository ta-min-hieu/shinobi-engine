# ADR-426: Báo cừu nhân online

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Báo cừu nhân online" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: Người trong danh sách cừu nhân (ADR-194) vào game ⇒ mình nhận một dòng báo; /baocuunhan bật/tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-194](ADR-194-Enemy-List.md) · [ADR-141](ADR-141-Zone-Map-HUD-And-Wasteland-Waypoints.md) |

---

## Decision

**D1:** khi một nhân vật vào world, server tìm những người đang ghi nhân vật đó trong danh sách cừu nhân (`pvp_enemy`, ADR-194) và chưa tắt báo, gửi họ (nếu đang online) dòng hệ thống "Cừu nhân X vừa vào game". Mặc định BẬT; bảng MỚI `enemy_alert_off(character_id)` (V184) — có dòng = đã tắt.

**D2 — Dây:** `SetEnemyAlertCommand{enabled, query_only}` / `SetEnemyAlertResponse{enabled}` **1168/1169**. Client `/baocuunhan [bat|tat]` (`/enemyalert`). Chế độ ẩn (ADR-383) chỉ áp với bạn bè, không áp với cừu nhân.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `enemy_alert_off` (V184), 1168/1169, mặc định bật | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `EnemyAlertPort/Facade`, `PostgresEnemyAlertAdapter`, `Lot62Commands.setEnemyAlert/onEnteredWorld`, `SocialExtrasCoordinator`.
2. ✅ Test: `Lot62IntegrationTest.enemyWatchersRespectMute`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: `enemyAlert:` query/off/on ⇒ `true/false/true`; thêm tạm dòng cừu nhân (BaoDo01 ghi ĐạoHữu01) ⇒ ĐạoHữu01 vào game ⇒ BaoDo01 nhận "Cừu nhân ĐạoHữu01 vừa vào game". Đã xoá dòng thử.
