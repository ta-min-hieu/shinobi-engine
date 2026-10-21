# ADR-065: Bộ pháp khí — thưởng theo số món khác mẫu của cùng một bộ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Bộ pháp khí (set bonus)" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: mặc đủ 2/4 món cùng bộ ⇒ cộng chỉ số, data-driven, hiện ở bảng nhân vật, không cần art). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("Bộ pháp khí 4/6/8 món: khuyến khích build đa dạng") · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) · [ADR-056](ADR-056-Character-Stat-Sheet-And-Combat-Power.md) · [ADR-064](ADR-064-Spirit-Jade-Sockets.md) |

---

## Context

Hai bậc trang bị đầu (Tập Sự, Ngoại Môn — V21) mỗi bậc đúng 6 món, nhưng mặc lẫn món của hai bậc
hay mặc trọn một bậc không khác gì nhau. GDD hứa thưởng bộ 4/6/8 món.

## Decision

**D1 — Dữ liệu thuần** (V67): `item_set` (mã, khoá tên), `item_set_member` (một mẫu thuộc nhiều nhất một
bộ — PK theo `item_template_id`), `item_set_bonus` (bậc `pieces_required` + 4 chỉ số). Đổi số = SQL.

**D2 — Đếm số MẪU khác nhau** đang mặc: hai trang sức cùng mẫu chỉ là một món của bộ. Các bậc cộng DỒN.

**D3 — Hai bộ đầu**: Tạp Dịch (novice_*) và Ngoại Môn (genin_*), bậc 2/4/6 (Q1 theo khuyến nghị — bộ 6 món
có sẵn; bậc 8 chờ có bộ 8 món). Vũ khí không thuộc bộ nào. Thưởng trọn bộ ≈ 30–50% chỉ số của chính 6 món.

**D4 — Thưởng bộ vào chung đường chỉ số trang bị**: `ComputeEquipmentBonusUseCase.withItemSets(port)` cộng
thêm vào `EquipmentBonus`; mặc/gỡ/attach tự tính lại như cũ. Thưởng KHÔNG nhân theo cường hoá.

**D5 — Giao thức, chỉ thêm**: `GetCharacterStatsResponse.item_sets = 18` (`ItemSetProgressView`: tên,
đang mặc/tổng, thưởng đang có). Không opcode mới, `protocolVersion` không tăng.

**D6 — Client**: bảng nhân vật (phím C) thêm một dòng mỗi bộ đang mặc ≥ 1 món.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Cột `set_code` trên `item_template` | Không chở được bảng bậc; vẫn cần bảng thưởng riêng |
| Thưởng nhân theo cường hoá | Chồng hệ số; GDD coi bộ là động lực "gom đủ", không phải trục sức mạnh chính |
| Hiện bộ trong tooltip món | Cần tooltip mới ở túi đồ; bảng nhân vật đủ cho bản đầu |

## Consequences

- Bảng nhân vật và Lực Chiến đã gồm thưởng bộ (cùng đường chỉ số trang bị).
- Bảng bộ đọc một lần lúc khởi động — đổi SQL thì khởi động lại server.

## Revisit Trigger

- Có bộ 8 món hoặc bộ riêng hệ linh căn; tooltip món cần hiện "thuộc bộ X".

## Technical Decisions

- Domain: `ItemSet`, `ItemSetTier`, `ItemSetProgress`, `ItemSets` (hàm thuần), `ItemSetPort`; adapter `PostgresItemSetAdapter`.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V67__item_sets.sql` · `ComputeEquipmentBonusUseCase` · `CharacterSheetCoordinator`
- Test: `ItemSetsTest`, `ComputeEquipmentBonusUseCaseTest#shouldAddItemSetBonus_*`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ✅ Kiểm sống 2026-10-21 bằng gameProbe (đo máu tối đa, chưa nhìn bảng C): `tanthua44` gỡ 5 món Genin rồi mặc lại từng món ⇒ 134 → 164 → 174 → 180 → 190 → 206; mốc 2 món (+10) và 6 món (+10) khớp từng bước.
