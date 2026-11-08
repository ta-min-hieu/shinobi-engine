# ADR-221: Nút "Đi tới" trong nhật ký nhiệm vụ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Nút đi tới trong nhiệm vụ" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: mục tiêu "gặp NPC" có nút tự đi tới, tiếp ADR-206). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-206](ADR-206-Auto-Walk-To-Npc.md) (tự tìm đường) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (nhật ký nhiệm vụ) |

---

## Decision

**D1 — NPC cần gặp lấy từ chính script quest.** Nhiệm vụ không có bảng mục tiêu có cấu trúc — mục tiêu nằm trong script
GraalJS. Server đọc script `/scripts/quest/<mã>.js` (đúng file `QuestScriptRunner` chạy) và lấy lần kiểm
`npc_code === "<mã NPC>"` đầu tiên trong sự kiện `npc_talked`; cache theo mã. Nhiệm vụ chỉ hạ quái / nhặt đồ ⇒ không có
NPC ⇒ không có nút. Chỉ là gợi ý đường đi, không cấp quyền gì (nói chuyện vẫn kiểm tầm như cũ).

**D2 — Protocol, chỉ thêm**: `QuestLogEntry.target_npc_code = 3`, chỉ điền cho nhiệm vụ **đang làm**.

**D3 — Client**: dòng nhiệm vụ đang làm có NPC cần gặp được thêm nút **"Đi tới"** cạnh nút Bỏ; bấm ⇒ tự tìm đường tới chỗ
đứng gần nhất của NPC đó (ADR-206, cùng đường `/den`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | NPC cần gặp đọc từ script quest, field 3 trên `QuestLogEntry`, nút "Đi tới" dùng tự tìm đường | Accepted |

## ADR cần tạo

- Khi quest có bảng mục tiêu có cấu trúc: thay đọc script bằng cột dữ liệu.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): `QuestNpcTargets`, `QuestTracker.LoggedQuest.targetNpcCode`, nút "Đi tới" ở
   `QuestLogPanel`, `AutoWalkCoordinator.walkToNpc`.
2. ✅ Test: `QuestNpcTargetsTest` (script thật: `report_to_outer_sect` ⇒ `senior_sister`, quest chỉ hạ quái ⇒ rỗng, mã lạ).
3. ✅ Kiểm sống gameProbe 2026-11-08: cho nhân vật thử hai nhiệm vụ đang làm ⇒ `ListQuests` trả
   `report_to_outer_sect->senior_sister`, `kill_training_dummies->-` (không có NPC); `senior_sister` có chỗ đứng ở x=2
   trong `npcLocations` (ADR-206) — nút "Đi tới" dẫn đúng chỗ. Đã xoá hai dòng tiến độ thử.
