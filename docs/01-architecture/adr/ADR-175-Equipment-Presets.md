# ADR-175: Lưu bộ trang bị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-06) — chủ dự án chọn "Lưu bộ trang bị" trong lượt hỏi lô 31 (AskUserQuestion; mô tả kèm: lưu 2-3 bộ trang bị (vd bộ PvE/PvP), bấm một nút để mặc cả bộ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (luật cấp khi mặc) · [ADR-043](ADR-043-Push-Max-Hp-On-Equipment-Change.md) (máu tối đa sau đổi đồ) |

---

## Decision

**D1 — Bộ là ảnh chụp MÓN cụ thể**: tối đa **3 bộ** mỗi nhân vật. "Lưu bộ n" chụp lại mọi ô đang mặc (ô → `item_id` của
chính món đó, không phải loại món) và ghi đè trọn bộ n. Ô không có trong bộ thì lúc mặc bộ giữ nguyên.

**D2 — Mặc bộ = mặc đồ thường lặp lại**: server gọi `InventoryFacade.equipItem` cho TỪNG món (khoá túi đồ, kiểm sở hữu,
đúng ô, đủ cấp — ADR-036 D4, cấp đọc từ DB), bỏ qua món đã đúng ô, rồi tính lại chỉ số + máu tối đa **một lần**
(ADR-043). Món đã bán/phân giải/không đủ cấp ⇒ bỏ qua, đếm `missing`. Không có luật mới nào để lách.

**D3 — Lưu (V140)**: `equipment_preset(character_id, preset_no 1..3, slot, item_id)`, khoá chính (nhân vật, bộ, ô).
KHÔNG khoá ngoại sang `inventory_item` — món mất thì dòng ở lại, danh sách bộ trả `item_template_id = 0` ("món đã mất").
Không chạm vàng/vật phẩm ⇒ không audit.

**D4 — Giao thức, chỉ thêm**: `SaveEquipmentPresetCommand`/`Response` 546/547, `ApplyEquipmentPresetCommand`/`Response`
548/549 (`equipped`, `missing`, `max_hp`), `GetEquipmentPresetsRequest`/`Response` 550/551.

**D5 — Client**: lệnh chat `/bodo` (hoặc `/preset`): không đối số ⇒ liệt kê; `/bodo luu <1-3>`; `/bodo <1-3>` mặc cả bộ.
⚠️ Lệch mô tả lúc hỏi ("bấm một nút"): bản này là lệnh chat như các tính năng lô 30; nút trong cửa sổ trang bị là việc sau.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 3 bộ, chụp món cụ thể, mặc qua đúng đường mặc đồ thường; V140; 546–551; `/bodo` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-06 (lô 31): V140, `EquipmentPresetPort`/`EquipmentPresetFacade`, `PostgresEquipmentPresetAdapter`,
   `EquipmentPresetCommands` 546–551, `EquipmentPresetCoordinator` `/bodo`.
2. ✅ Test: `EquipmentPresetIntegrationTest` (Postgres thật), `EquipmentPresetCoordinatorTest` (cú pháp, câu báo).
3. ✅ Kiểm sống gameProbe 2026-11-06 (bước `savePreset:<n>`, `applyPreset:<n>`, `listPresets`): ĐạoHữu01 lưu bộ 1
   (kiếm gỗ #5) ⇒ `OK`; bộ 2 (trường kiếm #21) ⇒ mặc 1 món, máu tối đa 168 gửi kèm; mặc lại bộ 2 ⇒ 0 món (đã đúng ô, không
   tính lại chỉ số); bộ 1 ⇒ về kiếm gỗ; bộ 3 trỏ món không còn ⇒ `thiếu=1`, danh sách hiện mẫu 0; bộ 4 / bộ 0 ⇒
   `INVALID_PRESET`.
4. Sau này: nút "Bộ 1/2/3" trong cửa sổ trang bị.
