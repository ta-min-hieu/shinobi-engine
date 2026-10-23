# ADR-073: Event hiệu ứng di chuyển trên chính mình — hết giật vị trí khi bị choáng/làm chậm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-23) — chủ dự án chọn "38: hết giật khi bị choáng" trong lượt hỏi lô 18 (AskUserQuestion; mô tả kèm: thêm event "hiệu ứng trên chính mình" để client dự đoán đúng — đổi protocol, chỉ THÊM opcode). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-23 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [docs/README.md](../../README.md) dòng 38 · [ADR-070](ADR-070-Flying-Sword-Mount.md) (opcode 310, cùng khuôn) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) · [CLIENT.md](../../04-client/CLIENT.md) |

---

## Context

Từ `7baec02` (2026-10-02) server áp choáng/làm chậm/tăng tốc lên nhân vật ĐANG chạy
(`CharacterLocomotionSystem`), nhưng client không biết mình đang dính hiệu ứng gì: nó vẫn dự đoán bằng tốc độ
gốc × phi kiếm. Bị choáng 2,5 giây giữa lúc giữ phím thì client chạy thêm ~12 m rồi bị kéo ngược về chỗ server
giữ — "giật vị trí" 12–15 m, chủ yếu trong PvP.

## Decision

**D1 — Opcode 311 `MovementModifierChangedEvent { float status_speed_multiplier = 1; bool immobilized = 2; }`**,
chỉ gửi cho CHÍNH nhân vật đó. `status_speed_multiplier` là tích các hiệu ứng tốc độ đang có
(`StatusEffects.moveSpeedMultiplier`) — KHÔNG gồm phi kiếm (đã có 310); `immobilized` = đang choáng hoặc đã gục.
Chỉ THÊM opcode, `protocolVersion` không tăng; client cũ bỏ qua gói lạ.

**D2 — Phát khi đổi, không phát mỗi tick**: `CharacterLocomotionSystem` nhớ giá trị đã báo gần nhất trên
`MoveIntentComponent` (hai field nguyên thuỷ, không cấp phát mỗi tick) và chỉ ghi một kết quả khi giá trị đổi —
lúc trúng hiệu ứng, lúc hết hạn. World xả hàng đợi đó sau tick như `HealthRegenSystem` (ADR-046), gateway gửi qua
`WorldOutboundSink#onMovementModifierChanged`.

**D3 — Client nhân vào dự đoán**: `LocalIntentSender` tính `vận tốc = hướng × MOVE_SPEED × phi kiếm × hệ số
hiệu ứng`, bằng 0 khi `immobilized`. Vẫn gửi hướng đang giữ như cũ — server giữ ý định để hết choáng là chạy
tiếp. Lệch nhỏ còn lại (độ trễ mạng tới lúc gói tới) do đối chiếu `MoveResponse` sửa như mọi khi.

**D4 — Đổi World / vào lại**: World mới tạo thực thể mới không hiệu ứng ⇒ client đặt lại hệ số 1 / không bị
khoá khi rời màn world (cùng chỗ 310 tự đặt về 0).

## Alternatives Considered

- **(b) client gửi lại `MoveCommand` định kỳ** — không chữa được gì: vấn đề là client KHÔNG BIẾT tốc độ thật,
  không phải server thiếu ý định.
- **Gửi danh sách hiệu ứng đầy đủ** (loại, độ lớn, thời hạn) — cần cho HUD biểu tượng hiệu ứng sau này, nhưng
  dự đoán di chuyển chỉ cần hai số. Để ADR riêng khi có giao diện hiệu ứng.

## Consequences

- Hết giật vị trí khi bị khống chế, trong giới hạn độ trễ một chiều.
- Thêm một gói nhỏ mỗi lần trúng/hết hiệu ứng — không đáng kể so với 59.760 gói/giây đo ở BENCHMARK §5.1.

## Revisit Trigger

- Có giao diện biểu tượng hiệu ứng ⇒ mở rộng thành event hiệu ứng đầy đủ.
- Có hiệu ứng đổi hướng/đẩy lùi ⇒ hai số này không đủ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Opcode 311, chỉ gửi cho chính mình | Accepted |
| 2 | Phát khi đổi, không phát mỗi tick | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [NETWORK.md](../../05-server/NETWORK.md) · [CLIENT.md](../../04-client/CLIENT.md) · [GAMEPLAY.md](../../05-server/GAMEPLAY.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-23 (lô 18) — `CharacterLocomotionSystem.drainChanges`, `WorldOutboundSink#onMovementModifierChanged`, `LocalIntentSender#onMovementModifier`; test `CharacterStatusMovementTest`.
2. ✅ **Kiểm sống 2026-10-23** (hai gameProbe, trận luận kiếm thật: `tanthua44` hệ Thuỷ ném `thuy_phong_toa` lên Benkei): Benkei nhận `311 statusSpeed=0.60` 5 ms sau `406 DEBUFF`, rồi `311 statusSpeed=1.00` đúng 4,0 s sau (hết hiệu ứng); người ra đòn nhận 0 gói 311. Trận kết thúc giữa lúc còn hiệu ứng thì chỉ có 308 — client tự đặt lại (D4).
