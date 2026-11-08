# ADR-253: Lịch sử nhặt đồ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Lịch sử nhặt đồ" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: xem 20 lần nhặt đồ gần nhất (server đọc audit sẵn có, không bảng mới)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-233](ADR-233-Enhance-History.md) (cùng khuôn đọc audit) · [ADR-204](ADR-204-Loot-Filter.md) |

---

## Decision

**D1 — Đọc audit, không bảng mới.** Mỗi lần rơi đồ đã ghi audit `LOOT_GRANT {source, itemTemplateId, quantity}` từ
trước. `GetLootHistoryRequest{}` / `GetLootHistoryResponse{repeated LootHistoryEntry{item_template_id, quantity, source,
at_epoch_seconds}}` 566/567 (dải Inventory) trả **20 dòng mới nhất** của chính mình. Món bị lọc tự bán (ADR-204) vẫn
hiện (nó đã rơi). Client `/lichsunhat` (hoặc `/lootlog`) in tên món (catalog vật phẩm) × số lượng, từ đâu.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 566/567 đọc audit `LOOT_GRANT`, 20 dòng; `/lichsunhat` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `ItemHistoryPort.lootHistory`, `PostgresItemHistoryAdapter`, `Lot40Commands.lootHistory`, `LootHistoryCoordinator`.
2. ✅ Test: `Lot40IntegrationTest.readsLootGrantsNewestFirst`.
3. ✅ Kiểm sống gameProbe 2026-11-08: `lootLog` của ĐạoHữu01 ⇒ 10 dòng (vd `1x1@training_dummy`, `6x1@training_dummy`) khớp audit `LOOT_GRANT`.
