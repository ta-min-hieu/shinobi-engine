# ADR-086: Kỹ năng linh thú

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Kỹ năng linh thú" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: linh thú đang mặc có 1 kỹ năng thụ động theo loài — Linh Hồ: % đánh thêm; Huyền Quy: % chắn sát thương; bậc 2 mạnh hơn). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-072](ADR-072-Spirit-Pets.md) · [ADR-078](ADR-078-Pet-Evolution.md) |

---

## Decision

**D1 — Một kỹ năng mỗi loài** (`PetSkill`, feature-inventory): **Linh Hồ** — 10% đòn đánh thường gây thêm 50% sát
thương; **Huyền Quy** — 10% chắn nửa sát thương đòn đánh thường nhận vào (tối thiểu 1). Bậc tiến hoá 2 gấp đôi xác suất
(20%). Chỉ đòn đánh thường — chiêu thức không đi qua ở bản đầu. Số là PLACEHOLDER cân bằng.

**D2 — Đường dữ liệu**: gateway tính xác suất từ linh thú đang mặc (`PetSkillEffects`) và gửi vào world qua
`EquipmentBonusSnapshot.Extras` (gom tốc độ phi kiếm + hai xác suất linh thú — record con để không vượt trần tham
số); world chép vào `CombatStatsComponent` lúc áp chỉ số; `PetSkills` (hàm thuần) chạy trong `CombatSystem` sau khi
tính đòn. Random chỉ ở server.

**D3 — Không đổi protocol, không đổi schema** — sát thương đã khuếch đại/chắn đi trong gói kết quả đòn như mọi đòn khác.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Linh thú tự ra đòn (thực thể ECS) | Cần AI + model + đồng bộ mạng (ADR-072 đã loại cho bản đầu) |
| Kỹ năng lưu trong DB theo mẫu | Hai loài, hai kỹ năng — bảng tĩnh cạnh `PetPolicy` đủ; chuyển DB khi có thêm loài |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Kỹ năng theo loài trên đòn thường, bậc 2 ×2 xác suất, qua `Extras` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `PetSkill` · `PetSkillEffects` · `PetSkills` · `EquipmentBonusSnapshot.Extras`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20).
2. ✅ Kiểm sống 2026-10-24: đeo Huyền Quy, đứng chịu đòn thường bandit (13/đòn) ⇒ 2/30 đòn bị chắn còn 6 (~7%, kỳ vọng 10%). Linh Hồ CHƯA quan sát được trực tiếp (Kagemaru Lv41 giết bandit một nhát, sát thương bị kẹp theo máu còn lại) — cùng đường `Extras` → `CombatStatsComponent` với Huyền Quy, phủ bằng test đơn vị `PetSkills`.
