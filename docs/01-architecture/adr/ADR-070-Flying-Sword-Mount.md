# ADR-070: Phi kiếm — ô trang bị MOUNT, chỉ cộng tốc độ di chuyển

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Phi kiếm (chỉ tốc độ)" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: +% tốc độ, KHÔNG cộng chỉ số, bản đầu chưa có model — chỉ hiệu ứng + icon tạm). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("Phi kiếm/linh cầm: chỉ tốc độ + thẩm mỹ, KHÔNG cộng chỉ số") · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) |

---

## Decision

**D1 — Ô trang bị thứ 8 `MOUNT`** (`EquipmentSlotType.MOUNT`, `EQUIPMENT_SLOT_MOUNT = 8`): mặc/gỡ bằng
đường trang bị sẵn có. Không thao tác "cưỡi/xuống" riêng — đang mặc là đang dùng (Q1 theo khuyến nghị: bản đầu
không có model cưỡi, một nút bật/tắt không có gì để nhìn).

**D2 — Chỉ tốc độ**: cột `item_template.move_speed_percent` (V71, 0–100). Hai phi kiếm bán ở cửa hàng tông môn:
Mộc Phi Kiếm +20% (cấp 15, 8.000) và Ngọc Phi Kiếm +35% (cấp 25, 30.000). Không cộng công/thủ/máu, không cường
hoá được.

**D3 — Server quyết tốc độ**: `EquipmentBonusSnapshot.moveSpeedPercent` đi cùng chỉ số trang bị tới world
(mọi đường attach qua `CharacterLoadout`); `CharacterLocomotionSystem` và `handleMoveIntent` nhân
`MOVE_SPEED_MPS × (1 + %/100)` trước hệ số hiệu ứng trạng thái. Áp cả trong chiến đấu, hầm ngục, đấu trường.

**D4 — Client dự đoán đúng tốc độ**: opcode **310** `MoveSpeedChangedEvent { move_speed_percent }` — gửi lúc
vào world và sau mỗi lần mặc/gỡ đồ. Client nhân vào vận tốc dự đoán; lệch thì server sửa như mọi khi.

**D5 — Hiển thị**: ô "Phi kiếm" trong cửa sổ túi đồ; chưa có model/hiệu ứng cưỡi (việc art riêng).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Lệnh cưỡi/xuống + mất tốc khi trúng đòn | Cần trạng thái + đồng bộ dự đoán phức tạp; không có model để thấy khác biệt |
| Vật phẩm "dùng" cho buff tốc độ tạm thời | Dùng là tiêu hao; phi kiếm là đồ giữ lâu |
| Cộng chỉ số nhỏ | GDD cấm — pay-for-cosmetic sạch |

## Consequences

- Tốc độ tối đa thực tế 6,75 m/s (5 × 1,35) — đi trục chính nhanh hơn; tầm aggro quái theo khoảng cách không đổi.

## Revisit Trigger

- Có model cưỡi; muốn mất tốc khi vào chiến đấu; PvP thấy chênh lệch tốc độ bất công.

## Technical Decisions

- `MountPort` (feature-inventory domain) + adapter đọc `move_speed_percent`; `InventoryFacade.moveSpeedPercent`.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V71__mounts_and_pets.sql` · `CharacterLocomotionSystem` · `LocalIntentSender`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ✅ Kiểm sống 2026-10-21 bằng gameProbe: mặc Mộc Phi Kiếm ⇒ opcode 310; đi 28 m trong 4,74 s ≈ 5,9 m/s (gốc 5, +20% = 6). Ghi chú: mặc linh thú cũng phát lại 310 (không đổi tốc độ, vô hại).
3. ⏳ Art: model/hiệu ứng phi kiếm dưới chân (chủ dự án).
