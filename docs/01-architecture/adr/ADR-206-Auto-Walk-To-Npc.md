# ADR-206: Tự tìm đường tới NPC

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Tự tìm đường" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: gõ tên NPC hoặc bấm mục tiêu nhiệm vụ, nhân vật tự đi tới đó). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (chỉ đi trái/phải) · [ADR-131](ADR-131-Blacksmith-And-Merchant-NPCs.md) (NPC động) · `NpcProximity` (tầm nói chuyện NPC, quyết định 2026-10-11) |

---

## Lệch so với mô tả lúc hỏi

Mô tả có "bấm mục tiêu nhiệm vụ". Mục tiêu nhiệm vụ trên dây hiện chỉ là mã + tiến độ, nhiều mục tiêu không phải NPC
(hạ quái, nhặt đồ), nên lần này chỉ làm **theo tên NPC** qua lệnh chat. Nối nút trong nhật ký nhiệm vụ vào cùng đường đi
này là việc client thuần, khi cần thì làm sau (không đổi protocol).

## Decision

**D1 — Server chỉ cho biết NPC đứng đâu.** `ListNpcLocationsRequest{}` / `ListNpcLocationsResponse{in_main_world,
repeated NpcLocation{npc_code, x, y}}` 626/627 (dải Quest/NPC). Danh sách = mọi chỗ đứng NPC tĩnh của world chính
(`npc_spawn`) + NPC động đang đứng (thương nhân du hành). Đang ở hầm ngục/đấu trường ⇒ `in_main_world=false`, rỗng.
Đây là thông tin công khai (ai cũng đi bộ tới thấy được), không cấp quyền gì: nói chuyện vẫn phải qua kiểm tầm của
`NpcProximity`.

**D2 — Đi là đi bình thường.** World là một trục trái/phải (ADR-033), nên "tìm đường" = lái hướng −1/0/1 về phía toạ
độ X đích. `LocalIntentSender` thay phím bằng hướng tới đích; server nhận đúng `MoveIntent` như khi người chơi tự bấm
phím — **không có lệnh dịch chuyển, không có gì server phải tin thêm**. Dừng khi:
- tới trong 2,5 m quanh đích (trong tầm nói chuyện 5 m);
- người chơi bấm phím đi (theo phím, huỷ tự đi);
- đổi World;
- 2 giây liền không nhích được (vướng địa hình). Đứng im vì choáng không tính.

**D3 — Client**: `/den <tên NPC>` (hoặc `/walkto`). Tên so khớp chữ thường, **không dấu**, một phần (`/den chu hoanh`),
hoặc đúng mã NPC. NPC đứng nhiều chỗ (đệ tử trực trạm) ⇒ chọn chỗ gần nhất. `/den` không kèm gì ⇒ liệt kê NPC ở world
chính. Các dòng chat báo đang đi (kèm khoảng cách), đã tới, đã dừng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 626/627 liệt kê chỗ đứng NPC world chính; tự đi = hướng −1/0/1 do client lái; `/den`, `/walkto` | Accepted |

## ADR cần tạo

- Không. Nút "đi tới" trong nhật ký nhiệm vụ dùng lại đúng đường này.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): `NpcProximity.placementsVisibleTo`, `Lot34Commands.listNpcLocations`,
   `AutoWalkTarget`, `AutoWalkCoordinator`, `LocalIntentSender.autoWalkDirection`.
2. ✅ Test: `NpcProximityTest` (tĩnh + động, rỗng ngoài world chính), `AutoWalkCoordinatorTest` (khớp không dấu, chỗ gần
   nhất, vòng đời đích).
3. ✅ Kiểm sống gameProbe 2026-11-07: `npcLocations` khi ĐạoHữu01 ở world chính ⇒ `in_main_world=true`, đủ 12 chỗ đứng
   (trưởng lão −5, sư tỷ 2, thợ rèn −11, 9 trạm tiếp dẫn 83…368) khớp đúng bảng `npc_spawn`. Phần lái nhân vật nằm ở
   client (không có client đồ hoạ trong môi trường kiểm sống) — kiểm bằng test đơn vị; server chỉ thấy `MoveIntent`
   thường nên không có đường nào mới để lách.
