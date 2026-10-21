# ADR-072: Linh thú bản đầu — ô trang bị PET, có cấp riêng theo kinh nghiệm chủ nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Linh thú (pet) bản đầu" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: pet theo người, cộng chỉ số nhỏ, có cấp riêng; chưa có model 3D, bản đầu chỉ là chỉ số + UI). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("Linh thú: buff/damage phụ, level riêng, tiến hoá") · [ADR-070](ADR-070-Flying-Sword-Mount.md) · [ADR-064](ADR-064-Spirit-Jade-Sockets.md) |

---

## Decision

**D1 — Linh thú là trang bị ô `PET`** (`EQUIPMENT_SLOT_PET = 9`): mặc một con là nó đi theo (về số liệu). Hai
con đầu bán ở cửa hàng tông môn (V71): Linh Hồ (+3 công, +10 máu, 12.000) và Huyền Quy (+3 thủ, +3 kháng,
+15 máu, 12.000), cấp yêu cầu 20 (GDD: "Lv20 linh thú").

**D2 — Cấp linh thú 1–30** (`pet_state`, V71, theo `inventory_item.id`; chưa có dòng = cấp 1). Linh thú đang mặc
nhận ĐÚNG số EXP chủ nhân nhận khi hạ quái (cả phần chia nhóm, cả sự kiện x2). Mốc: cấp n → n+1 cần `100 × n`.
Chỉ số = chỉ số gốc × (1 + 10% × (cấp − 1)) — cùng công thức cường hoá; linh thú KHÔNG vào lò cường hoá.

**D3 — Đi đâu được**: như món đã khảm (ADR-064 D5) — có dòng `pet_state` thì thư/phường thị/kho từ chối (các
đường đó tạo lại dòng, sẽ mất cấp); giao dịch trực tiếp giữ nguyên dòng nên giữ cấp.

**D4 — Giao thức, chỉ thêm**: `EQUIPMENT_SLOT_PET`; `GetCharacterStatsResponse.pet = 19`
(`PetView`: khoá tên, cấp, EXP trong cấp, EXP cần). Lên cấp linh thú ⇒ gateway tính lại chỉ số + báo một dòng
chat. Không opcode mới.

**D5 — Client**: ô "Linh thú" trong túi đồ; một dòng ở bảng nhân vật (C). Chưa có model đi theo.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Linh thú là thực thể ECS đánh cùng | Cần AI, model, đồng bộ mạng — quá lớn cho bản đầu |
| Hệ "trứng ấp" | Thêm thời gian chờ + UI; cửa hàng đủ cho bản đầu |
| Cấp linh thú = enhance_level | Trộn hai nghĩa vào một cột; lò cường hoá thành đường lên cấp thứ hai |

## Consequences

- Mỗi lượt hạ quái của người đang mặc linh thú thêm một câu UPDATE `pet_state`.

## Revisit Trigger

- Có model linh thú; tiến hoá; kỹ năng linh thú.

## Technical Decisions

- `PetPort`/`PetPolicy` (feature-inventory domain), `PostgresPetAdapter`; `PetHooks` (gateway) nối EXP chủ nhân.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V71__mounts_and_pets.sql` · `ComputeEquipmentBonusUseCase` · `CharacterSheetCoordinator`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ⏳ Kiểm sống: mua Linh Hồ, mặc, hạ vài quái, bảng nhân vật thấy linh thú lên cấp.
3. ⏳ Art: model linh thú (chủ dự án).
