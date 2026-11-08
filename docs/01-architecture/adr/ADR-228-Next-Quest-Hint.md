# ADR-228: Gợi ý nhiệm vụ tiếp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Gợi ý nhiệm vụ tiếp" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: lệnh /tieptheo cho biết nên làm nhiệm vụ nào / gặp NPC nào tiếp theo). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-221](ADR-221-Quest-Go-To-Button.md) (NPC cần gặp) · [ADR-206](ADR-206-Auto-Walk-To-Npc.md) (tự tìm đường) · [ADR-055](ADR-055-Daily-Quests.md) (nhiệm vụ ngày) |

---

## Decision

**D1 — Thứ tự ưu tiên**: (1) nhiệm vụ đang làm — kèm NPC cần gặp (ADR-221) nếu có; (2) nhiệm vụ đã xong chưa nhận
thưởng; (3) nhiệm vụ cốt truyện đầu tiên (thứ tự thêm vào game) chưa nhận, không phải nhiệm vụ ngày, nhiệm vụ tiên quyết
đã trả xong, và có NPC giao; (4) không có gì.

**D2 — NPC giao đọc từ script hội thoại**: hành động `accept_quest:<mã>` trong `/scripts/dialogue/<NPC>.js` của mọi NPC
đặt tĩnh ở world chính. Điều kiện cấp / cốt truyện riêng nằm trong script nên gợi ý có thể là nhiệm vụ NPC chưa chịu giao
vì chưa đủ cấp — câu gợi ý nói rõ "có thể cần đủ cấp". Danh mục + tiên quyết đọc từ `quest_template` (một lần).

**D3 — Protocol**: `GetNextQuestHintRequest{}` / `GetNextQuestHintResponse{kind, quest_code, npc_code}` 628/629.

**D4 — Client**: `/tieptheo` (`/next`) in một dòng; `/tieptheo di` (`/next go`) còn tự đi tới NPC.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đang làm → chờ nhận → nhiệm vụ cốt truyện kế tiếp có NPC giao; 628/629; `/tieptheo [di]` | Accepted |

## ADR cần tạo

- Khi nhiệm vụ có cột "cấp tối thiểu" có cấu trúc: lọc theo cấp ở D1(3).

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): `QuestCatalogPort`/`Facade`, `PostgresQuestCatalogAdapter`, `QuestGivers`,
   `NextQuestHint`, `NpcProximity.staticNpcCodes`, `NextQuestHintCoordinator`.
2. ✅ Test: `NextQuestHintTest` (thứ tự ưu tiên; NPC giao từ script thật), `Lot37IntegrationTest` (danh mục),
   `ChatFilterTest` (dòng gợi ý).
3. ✅ Kiểm sống gameProbe 2026-11-08: nhân vật mới và ĐạoHữu01 (chưa nhận nhiệm vụ cốt truyện nào) `nextQuest` ⇒
   `NEW quest=kill_training_dummies npc=town_elder` (đúng nhiệm vụ đầu tiên, NPC giao đọc từ `town_elder.js`); trường hợp
   đang làm / chờ nhận kiểm ở `NextQuestHintTest`.
