# ADR-195: Tóm tắt khi chết

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Tóm tắt khi chết" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: khi chết thì hiện ai/quái nào gây bao nhiêu sát thương trong 10 giây cuối và bằng kỹ năng gì). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-117](ADR-117-Revive-In-Place.md) (cửa sổ hồi sinh) · [ADR-194](ADR-194-Enemy-List.md) |

---

## Vì sao ở server

Client đã nhận mọi gói `AttackResultEvent`/`SkillResultEvent` nhắm vào mình, nhưng **không biết tên người chơi xung
quanh** (`CharacterSpawnedEvent` không mang tên) — làm thuần client thì người chơi chỉ hiện được bằng số. Vì vậy gateway
ghi đòn và tra tên; quái thì client tự đặt tên theo mã đã thấy trong gói spawn (server không phải giữ bảng id → mã quái
sống mãi).

## Decision

**D1 — Ghi gì**: mỗi đòn **trúng** (không trượt, sát thương > 0) vào một nhân vật — đòn đánh thường hoặc kỹ năng loại
DAMAGE — vào hàng đợi RAM của người bị trúng (`DeathRecapHooks`, trần **64** đòn/nhân vật). Ghi ở
`ConnectedCharacterRegistry.onAttackResult/onSkillResult` lúc người nhận gói chính là người bị trúng (mỗi đòn đúng một
lần). Sát thương theo thời gian (độc, bỏng, dậm đất qua `HealthChangedEvent`) KHÔNG có trong tóm tắt.

**D2 — Lúc bị hạ**: gom các đòn trong **10 giây** cuối theo kẻ ra đòn (tổng sát thương, số đòn, kỹ năng gây nhiều nhất),
xoá hàng đợi, rồi trên Virtual Thread tra tên người chơi và gửi `DeathRecapEvent` 415 cho chính người bị hạ. Áp cho world
chính và hầm ngục (sink hầm ngục đi qua registry); đấu trường không (sink riêng). Rời world ⇒ bỏ hàng đợi.

**D3 — Giao thức, chỉ thêm**: `DeathRecapEvent{killer_entity_id, repeated DeathRecapEntry{attacker_entity_id,
attacker_name, damage, hits, top_skill_code}, window_seconds}` 415. Không DB, không migration.

**D4 — Client**: in vào khung chat: dòng đầu "Bạn đã tử trận. Sát thương nhận trong 10 giây cuối:", mỗi kẻ ra đòn một
dòng (nhiều nhất trước), dòng kẻ kết liễu có nhãn. Tên kỹ năng qua khoá `hud.skill.<mã>`, tên quái qua `hud.monster.<mã>`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Hàng đợi đòn RAM ở gateway (64/nhân vật, cửa sổ 10 giây), event 415 cho người bị hạ, tên quái phía client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): `DeathRecapHooks`, móc ở `ConnectedCharacterRegistry`, `DeathRecapCoordinator`.
2. ✅ Test: `DeathRecapHooksTest`, `DeathRecapCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (PvP thật ở x=320): ĐạoHữu01 hạ BaoDo01 sau 3 đòn trúng ⇒ BaoDo01 nhận 415
   `killer=2 window=10s entries=[2/ĐạoHữu01:102dmg/3hits/]` (đòn trượt không tính, tên người chơi do server tra). Dữ liệu
   thử (sát khí, cừu nhân, vị trí) đã trả về như cũ.
4. Chờ chủ dự án: cửa sổ 10 giây và việc chưa tính sát thương theo thời gian.
