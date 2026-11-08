# ADR-242: Xem trước thưởng nhiệm vụ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Xem trước thưởng nhiệm vụ" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: xem trước EXP/vàng/vật phẩm sẽ nhận của một nhiệm vụ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-055](ADR-055-Daily-Quests.md) (`ctx.give_gold`) · [ADR-016](ADR-016-Scripting-Engine.md) (sandbox script) · [ADR-228](ADR-228-Next-Quest-Hint.md) |

---

## Decision

**D1 — Chạy `on_claim` không ghi gì.** Thưởng nhiệm vụ nằm trong script (`ctx.give_exp/give_gold/give_item`), không
có cột DB nào. Khi lĩnh thật, `QuestScriptRunner` chỉ **ghi lại** các lời gọi đó vào một danh sách rồi
`ClaimQuestRewardUseCase` mới mở giao dịch trao. Xem trước = chạy đúng đoạn đó rồi **bỏ** danh sách
(`PreviewQuestRewardUseCase`) — không giao dịch, không đổi trạng thái, không đốt khoá chống trùng. Một nguồn sự thật:
không có bảng thưởng thứ hai để lệch với script.

**D2 — Theo tiến độ hiện tại** của người hỏi (chưa nhận ⇒ `"{}"`). Script mà thưởng tuỳ tiến độ và ném lỗi với tiến độ
này ⇒ `UNAVAILABLE` (không đoán). Mã lạ ⇒ `NOT_FOUND`.

**D3 — Protocol** `GetQuestRewardPreviewRequest{quest_code}` / `GetQuestRewardPreviewResponse{quest_code, outcome, exp,
gold, repeated ItemRewardView items}` 630/631 (dải Quest/NPC, dùng lại `ItemRewardView`). Client `/xemthuong <mã>`
(hoặc `/rewards`); hàm `preview(code)` để nút trong nhật ký nhiệm vụ gọi sau.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 630/631 chạy `on_claim` vào danh sách rồi bỏ — không ghi gì; theo tiến độ hiện tại; `/xemthuong` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `PreviewQuestRewardUseCase`, `QuestRewardPreviewFacade`, `Lot39Commands.questPreview`, `QuestRewardPreviewCoordinator`.
2. ✅ Test: `Lot39IntegrationTest.previewsSeededQuestRewardWithoutGrantingAnything` (kill_training_dummies ⇒ 500 EXP + 5 kunai, túi vẫn trống), `Lot39CoordinatorsTest`.
3. ✅ Kiểm sống gameProbe 2026-11-08: `questPreview:kill_training_dummies` ⇒ `OK exp=500 vàng=0 kunaix5` (đúng `on_claim` của script), mã lạ ⇒ `NOT_FOUND`; túi không đổi.
