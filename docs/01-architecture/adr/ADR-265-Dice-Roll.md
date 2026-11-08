# ADR-265: Gieo xúc xắc vào chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Gieo xúc xắc vào chat" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: /gieo [max]: server bốc số 1–100 (hoặc 1–max), phát cho nhóm/khu vực gần). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-150](ADR-150-Group-Loot-Rolls.md) (xúc xắc chia đồ — khác: lệnh này không chia gì) |

---

## Decision

**D1 — Server bốc số** (luật vùng server: random chỉ sinh ở server). `RollDiceCommand{max}` → `RollDiceResponse{outcome,
value, max}` (814/815, dải Chat). `max = 0` ⇒ 100; hợp lệ 2–1000 (`INVALID_MAX`). Giới hạn 1 lần / 3 giây mỗi nhân vật
(`RATE_LIMITED`); bảng nhịp tự dọn khi quá 4096 mục.

**D2 — Người thấy kết quả: cả nhóm đang online; không có nhóm ⇒ chỉ mình.** Lựa chọn khi hỏi ghi "nhóm/khu vực gần" —
**khu vực gần CHƯA làm**: tin Khu vực đi qua world tick (AoI), không gửi thẳng từ gateway được như kênh nhóm; làm đúng cần
đi đường `onAreaChatMessage` của world, việc lớn hơn phần còn lại của lệnh này. Ghi ở "Việc cần làm tiếp theo".

**D3 —** Client `/gieo [max]` (hoặc `/roll`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 814/815; 1..max (2–1000, mặc định 100); 3 giây/lần; tới nhóm (khu vực gần: chưa) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `PartySignals.rollDice`, `Lot42Commands.rollDice`, `DiceRollCoordinator`.
2. ✅ Test: `PartySignalsTest.diceRangeAndCooldown`.
3. ⏳ Chưa làm: phát kết quả cho người ở **khu vực gần** khi không có nhóm (cần đi đường chat Khu vực qua world tick — xem D2). Chờ chủ dự án quyết có cần không.
