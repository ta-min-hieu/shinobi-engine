# ADR-113: Trấn Yêu Tháp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Trấn Yêu Tháp" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: tháp leo tầng: mỗi tầng một trận quái mạnh dần (World riêng như Bí Cảnh), lưu tầng cao nhất, thưởng thư lần đầu qua mỗi 5 tầng, bảng xếp hạng tầng; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-026](ADR-026-Dungeon-Instance-Model.md) (World riêng trong cùng JVM) · ADR-051 (event đổi World) · ADR-057 (Bia Anh Danh) |

---

## Decision

**D1 — Tầng là dữ liệu (V91)**: `tower_floor(floor, monster_code, monster_count)` — 20 tầng, mỗi tầng 1–3 con của MỘT
loài quái đã có, loài mạnh dần theo tầng (bù nhìn → hộ pháp Trúc Cơ). `tower_reward(floor, gold, item_template_id,
quantity)` ở tầng 5/10/15/20.

**D2 — Một người một tháp**: vào tháp = dời vào World riêng (cùng cơ chế World hầm ngục, ADR-026 — một World mới mỗi
lượt, không có boss, không quái tự hồi sinh), đứng ở tầng `cao nhất + 1`. Hạ hết quái ⇒ ghi tầng (`character_tower`
khoá chính theo nhân vật, `best_floor` chỉ tăng) ⇒ dời về world chính, kèm thư thưởng nếu là LẦN ĐẦU qua mốc 5/10/15/20.
Chết trong tháp ⇒ về world chính (ADR-051), không mất gì. Không giới hạn lượt.

**D3 — Giao thức, chỉ thêm**: 1505/1506 `EnterTowerCommand`/`Response{outcome, floor}`, 1507 `TowerFloorClearedEvent{floor,
best_floor, rewarded}`, 1508/1509 `GetTowerStatusRequest`/`Response{best_floor, next_floor, top[]}` (top 10 tầng cao
nhất). Rời tháp giữa chừng dùng lại `ExitDungeonCommand` (1502) — server nhận ra World tháp.

**D4 — Client**: tab "Trấn Yêu Tháp" trong cửa sổ Hằng ngày (E): tầng cao nhất, tầng kế, bảng top, nút Vào tháp.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 20 tầng dữ liệu, World riêng mỗi lượt, best_floor chỉ tăng, thư mốc 5/10/15/20, top 10 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-25 (lô 22 phần 3). ⚠️ **Migration là V93**, không phải V91 như D1 ghi. Tháp đi CHUNG
   `DungeonInstanceRegistry` (khoá instance `-characterId`, id nhóm luôn dương) thay vì một registry thứ tư — mọi
   đường định tuyến/rớt mạng/gục/`ExitDungeonCommand` dùng lại nguyên vẹn. server-world thêm `spawnMonsterOnce`
   (quái thường không thuộc điểm spawn ⇒ không hồi sinh; tách `createMonsterEntity` dùng chung với `spawnMonster`).
   Con cuối chết ⇒ `TowerHooks` ghi tầng (UPSERT có điều kiện `best_floor < floor`, thưởng mốc trao đúng một lần) +
   gửi 1507 TRƯỚC khi đưa về world chính. Tab thứ 8 của cửa sổ E ⇒ hàng tab của `ReportPanel` nay xuống dòng mỗi 5 tab.
2. ✅ Kiểm sống 2026-10-25 (gameProbe `enterTower,towerFight:<giây>`): Kagemaru qua tầng 1→6; mỗi tầng 1507 tới TRƯỚC 308 về world chính; `GetTowerStatus` lên dần 1..6 và bảng top có Kagemaru; thư mốc DUY NHẤT ở tầng 5 (1.000 linh thạch + 2 Tẩy Luyện Thạch), tầng 6 `rewarded=false`. Đang ở tháp mà bấm vào lại ⇒ `ALREADY_IN_DUNGEON`.
