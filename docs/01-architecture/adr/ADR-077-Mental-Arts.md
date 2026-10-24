# ADR-077: Tâm pháp bị động — học và nâng bằng điểm công pháp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Tâm pháp bị động" trong lượt hỏi lô 19 (AskUserQuestion; mô tả kèm: GDD §5 mỗi linh căn 2 tâm pháp bị động, mở bằng điểm công pháp ADR-071, data-driven, gộp vào bảng chỉ số/Lực Chiến). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5](../../13-game-design/GAME-DESIGN-DOCUMENT.md) "3 pháp thuật chủ động + 2 tâm pháp bị động" · [ADR-071](ADR-071-Skill-Ranks.md) · [ADR-056](ADR-056-Character-Stat-Sheet-And-Combat-Power.md) |

---

## Context

`CombatConstants.FACTION_PASSIVES` đã có một gói chỉ số cố định theo linh căn (luôn bật, không chọn được). Điểm công
pháp (ADR-071) chỉ có chỗ tiêu là 4 chiêu chủ động — không có lựa chọn "mạnh chiêu hay dày máu".

## Decision

**D1 — Hai tâm pháp mỗi linh căn** (`dev.kage.server.world.MentalArts`, bảng tĩnh như `SkillRegistry`): một mở ở
Lv15 (tầng mở công pháp của GDD), một ở Lv30. Mỗi tâm pháp cộng một chỉ số theo bậc (công / thủ / kháng / máu tối
đa). Số trong bảng là PLACEHOLDER cân bằng.

**D2 — Bậc 0→5, chung quỹ điểm với chiêu**: chưa có dòng = bậc 0 (chưa học); mỗi bậc (kể cả bậc 1) tốn một điểm.
Lưu ở `character_skill_rank` hiện có (mã tâm pháp làm `skill_code`) — **không migration**. Điểm đã tiêu =
Σ(bậc chiêu − 1) + Σ bậc tâm pháp, chỉ trên linh căn hiện tại (ADR-071 D3: đổi hệ có quỹ mới, đổi về lấy lại bậc).

**D3 — Cộng PHẲNG theo bậc, đi chung ống chỉ số trang bị**: gateway gộp tâm pháp vào `EquipmentBonusSnapshot` ở
một chỗ duy nhất (`SkillRankSource#withMentalArts`, gọi từ `ConnectedCharacterRegistry#equipmentBonusOf`) ⇒ vào
world, mặc/gỡ đồ, linh thú lên cấp, đổi linh căn, bảng nhân vật và Lực Chiến đều thấy cùng một con số. Nâng tâm pháp
áp ngay vào world (kèm `HealthChangedEvent` nếu máu tối đa đổi).

> Lượt hỏi mô tả "cộng chỉ số %". Thi hành bằng cộng PHẲNG theo bậc: phần trăm cần một trục mới trong công thức chỉ
> số của server-world (máu tối đa theo cấp, ATK dẫn xuất) và lệch dần khi lên cấp mà không có ai tính lại. Phẳng
> cho cùng cảm giác "mỗi bậc mạnh thêm" mà không đổi công thức. Đổi sang % là Revisit Trigger bên dưới.

**D4 — Giao thức, chỉ thêm**: `SkillRankView.passive = 7`. Dùng lại 409–412 (danh sách + nâng). `protocolVersion`
không tăng.

**D5 — Client**: cửa sổ Công pháp (phím R) thêm 2 dòng "[Tâm pháp] tên — bậc n/5 · hiệu ứng mỗi bậc".

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Phần trăm chỉ số | Xem D3 |
| Quỹ điểm riêng cho tâm pháp | Mất chính sự lựa chọn — chiêu hay thân |
| Bảng DB `mental_art` | Kỹ năng hiện cũng tĩnh trong code (`SkillRegistry`); chuyển cả hai sang DB một lượt khi cần |

## Consequences

- Lv40 có 20 điểm; nâng tối đa 4 chiêu cần 16, hai tâm pháp cần 10 ⇒ phải chọn.
- Các số cân bằng trận (luật biên) chưa tính tâm pháp.

## Revisit Trigger

- Muốn cộng theo %; muốn điểm từ nhiệm vụ; đo lại luật biên có tâm pháp.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 2 tâm pháp/hệ, bậc 0–5, chung quỹ, cộng phẳng qua ống chỉ số trang bị | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `MentalArts` · `SkillRankUseCase` · `SkillRankSource` · `SkillRankCoordinator`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 19).
2. ⏳ Kiểm sống bằng gameProbe.
