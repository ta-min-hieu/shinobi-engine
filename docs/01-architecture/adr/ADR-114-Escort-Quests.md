# ADR-114: Nhiệm vụ hộ tống

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Nhiệm vụ hộ tống" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: loại mục tiêu quest mới: hộ tống NPC đi từ A tới B dọc trục, NPC có máu và bị quái nhắm; NPC chết = thất bại, nhận lại được; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-055 (nhiệm vụ ngày) · feature-quest/CLAUDE.md (mục tiêu loại 2/3 đi qua sự kiện, không đường vào mới) |

---

## Decision

**D1 — Đoàn hộ tống là thực thể của World**: nhận quest hộ tống ⇒ server sinh một thực thể "người được hộ tống" (có
máu, không đánh trả) ở điểm A trên world chính, đi thẳng theo trục X về B với tốc độ cố định **khi người hộ tống đứng
trong 6 m** (bỏ đi xa thì đoàn đứng chờ). Tham số đoàn ở bảng `escort_route(quest_code, start_x, end_x, hp,
speed)` (V91). Một người một đoàn.

**D2 — Bị quái nhắm**: quái ĐANG CÓ MỤC TIÊU (đang giao chiến) trong **2 m** quanh đoàn gây sát thương bằng công của nó
mỗi nhịp đánh lên đoàn — người hộ tống phải dọn quái dọc đường. Không đổi AI quái (không thêm loại mục tiêu mới).

**D3 — Kết quả là SỰ KIỆN quest**: đoàn tới B ⇒ sự kiện `escort_arrived{route}` vào `ProcessQuestEventUseCase` (đường vào
duy nhất, như `npc_talked`) ⇒ script quest `complete()`. Đoàn hết máu ⇒ sự kiện `escort_failed` ⇒ script xoá tiến độ;
server bỏ quest về "chưa nhận" (dùng lại đường bỏ quest ADR-054) để nhận lại được. Người hộ tống rời world chính/ngắt
kết nối ⇒ đoàn biến mất, quest về "chưa nhận".

**D4 — Nội dung**: quest ngày mới `daily_caravan_escort` (Chu Hoành nhờ đưa xe linh thảo từ tông môn tới trạm Linh
Mạch, x = 0 → 82).

**D5 — Giao thức, chỉ thêm**: 621..624 đã dùng ⇒ 625 `EscortStateEvent{entity_id, x, hp, max_hp, state}` (gửi người hộ tống
mỗi khi máu/trạng thái đổi và mỗi 1 giây khi đang đi). Client vẽ đoàn bằng hộp tạm (chưa có model) + thanh máu.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thực thể đoàn trong World, quái đang giao chiến trong 2 m gây sát thương, kết quả là sự kiện quest | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-25 (lô 22 phần 3). ⚠️ **Migration là V94** (không phải V91 như D1 ghi). server-world:
   `EscortCaravans` (không phải entity ECS — không đánh, không bị nhắm, không vào AoI; danh sách duyệt theo chỉ số, không
   cấp phát mỗi tick), `EscortRoute`, lệnh `startEscort`/`cancelEscort`, `EscortListener` nối riêng cho world chính
   (`useEscortListener`) thay vì thêm phương thức vào sink của gateway (đúng trần PMD 80 của `ConnectedCharacterRegistry`).
   Đoàn đi khi người hộ tống trong 6 m; mỗi 1 giây chịu tổng công của quái ĐANG CÓ MỤC TIÊU trong 2 m; rời World ⇒
   `ABANDONED`. feature-quest: `EscortRoutePort`/`EscortRouteFacade`, `QuestFacade.onEscortFinished` (sự kiện
   `escort_arrived`/`escort_failed` mang `quest_code`, qua khoá sự kiện). Gateway `EscortHooks`: nhận quest có tuyến ⇒
   xếp đoàn; 625 mỗi lần đổi; tới nơi ⇒ 612 COMPLETED + dòng hệ thống; vỡ/rời ⇒ sự kiện thất bại + bỏ quest (ADR-054);
   tự bỏ quest ⇒ huỷ đoàn lặng lẽ. Quest `daily_caravan_escort` (script + lựa chọn thoại Chu Hoành, 400 EXP / 80 linh
   thạch — đề xuất cân bằng). Bộ fuzz quest có loại mục tiêu `ESCORT` + lệnh `Escort`.
   **Lệch D5 (cần chủ dự án xem):** chưa vẽ hộp 3D tạm — đoàn hiện bằng dải chữ thường trực (dùng lại dải "đang ở Bí
   Cảnh", trống ở world chính): vị trí/đích, máu xe, đang đi/đứng chờ. Vẽ hộp cần presenter mới trong client 3D.
   ⚠️ Server khởi động lại giữa lúc hộ tống: quest ở lại IN_PROGRESS mà không có đoàn — người chơi bỏ quest (ADR-054) rồi
   nhận lại.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-30** (DB dev V94, server thật, bước probe mới `--afterQuest=escortFollow:<giây>` —
   đi kèm đoàn, đánh quái trong 6 m quanh xe; chạy SAU pha quest vì ngắt kết nối là đoàn vỡ):
   - Thành công (Kagemaru Lv42): nhận qua Chu Hoành (`about_town → daily_duty → accept_quest:daily_caravan_escort`),
     55 gói 625, hạ 5 quái dọc đường, xe tới `ARRIVED x=82/82 máu=274/300` (mất 26 ⇒ D2 chạy), 607 `COMPLETED`.
     Lĩnh thưởng: +400 EXP (87474→87874), +80 linh thạch, `COMPLETED → CLAIMED`.
   - Đứng xa (tanthua44, người ở x=−4): xe dừng `WAITING x=2` (đúng D1, >6 m). Ngắt kết nối ⇒ không còn hàng
     `character_quest_progress` của quest này (về "chưa nhận", D3).
   - Xe vỡ (tạm hạ `escort_route.max_hp` = 15 trên DB dev, đã trả lại 300): `FAILED x=17/82 máu=0/15` sau một nhát
     `wandering_bandit`, dòng hệ thống "Hộ tống thất bại…", quest về "chưa nhận". `max_hp` đọc lúc nhận quest, không cần
     khởi động lại server.
   - 🐛 **Lỗi tìm ra và đã vá (client):** vỡ/bỏ đoàn thì server bỏ quest mà KHÔNG gửi gói trạng thái quest nào ⇒
     nhật ký + góc màn hình vẫn hiện quest như đang làm tới khi relog. Vá: `QuestCoordinator` nghe 625
     `FAILED`/`ABANDONED` ⇒ `QuestTracker.onEscortEnded` gỡ quest rồi hỏi lại danh sách (không đổi giao thức).
