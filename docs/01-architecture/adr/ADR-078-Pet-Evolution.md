# ADR-078: Linh thú tiến hoá bằng Linh Thú Đan

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Linh thú tiến hoá" trong lượt hỏi lô 19 (AskUserQuestion; mô tả kèm: linh thú đủ cấp + vật phẩm "Linh Thú Đan" ⇒ tiến hoá bậc 2, chỉ số nhân hệ số; mở rộng ADR-072, thêm opcode). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-072](ADR-072-Spirit-Pets.md) (Revisit Trigger "tiến hoá") · [ADR-046](ADR-046-Health-Recovery.md) D5 (đường dùng vật phẩm) · GDD §5 "Linh thú tiến hoá" |

---

## Decision

**D1 — Bậc tiến hoá 1→2** (`pet_state.stage`, V75; dòng cũ = bậc 1). Bậc 2 nhân chỉ số linh thú **150%** (làm tròn
xuống), nhân SAU hệ số cấp (`PetPolicy.staged`). Cấp, EXP, trần cấp 30 giữ nguyên.

**D2 — Điều kiện**: linh thú ĐANG MẶC, cấp ≥ **20**, đang bậc 1, tiêu một **Linh Thú Đan** (`spirit_beast_pill`,
cửa hàng tông môn 20.000 — tham số SQL).

**D3 — Đi đường "dùng vật phẩm" có sẵn (512/513), KHÔNG thêm opcode** (khác mô tả lượt hỏi — đường dùng vật phẩm đã
làm đúng việc này: khoá túi đồ, kiểm món đang chào giao dịch, trừ một viên, audit, hồi chiêu dùng vật phẩm). Hiệu ứng
mới `EVOLVE_PET` (`ItemUseEffect`, cột `use_effect` vốn là chuỗi khớp enum). Kiểm linh thú dưới khoá túi đồ rồi
khoá dòng `pet_state` TRONG CÙNG transaction, TRƯỚC khi trừ viên đan ⇒ từ chối thì không mất gì. Từ chối trả
`UseItemResponse.error_key`: `error.no_pet_equipped` / `error.pet_level_too_low` / `error.pet_already_evolved`.

**D4 — Giao thức, chỉ thêm**: `ITEM_USE_EFFECT_EVOLVE_PET = 2`, `PetView.stage = 5`. Thành công ⇒ gateway tính lại
chỉ số như sau khi mặc đồ (kèm `HealthChangedEvent` nếu máu tối đa đổi).

**D5 — Client**: nút "Dùng" tự hiện cho mọi món có hiệu ứng; bảng nhân vật ghi "[Đã tiến hoá]".

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Opcode `EvolvePetCommand` riêng | Lặp lại toàn bộ khoá/kiểm/audit của đường dùng vật phẩm |
| Tiến hoá đặt lại cấp về 1 | Chỉ số tụt ngay sau khi tốn đan — cảm giác bị phạt |
| Nhiều bậc (3, 4…) | Bản đầu một bậc; `MAX_STAGE` + CHECK của V75 nới được |

## Consequences

- Linh thú bậc 2 cấp 30: Linh Hồ +11 công / +43 máu → +16 / +64.

## Revisit Trigger

- Có model linh thú (đổi ngoại hình theo bậc); thêm bậc; Linh Thú Đan chế từ lò đan (ADR-079).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bậc 2 = ×150%, cần cấp 20 + Linh Thú Đan, qua đường dùng vật phẩm | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V75__pet_evolution.sql` · `UseItemUseCase` · `PostgresCompanionAdapter` · `PetPolicy`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 19).
2. ✅ Kiểm sống 2026-10-24: luyện Linh Thú Đan ở lò đan rồi `--useItemCode=spirit_beast_pill` ⇒ `pet_state.stage` 1 → 2; Linh Hồ cấp 20: máu tối đa 353 → 367 (+14 = 43 − 29), công 95 → 99 (+4); viên thứ hai ⇒ `error.pet_already_evolved`, viên đan còn nguyên.
3. ✅ **Bug thật của ADR-072 tìm ra lúc kiểm (đã vá)**: chỉ số linh thú đi qua `EnhancementTable.enhancedBonus`, hàm chặn cấp ở +15 ⇒ linh thú cấp 17–30 ném `IllegalArgumentException` và nhân vật **không vào world được** (`error.select_character_failed`). Kiểm sống ADR-072 trước đó chỉ tới cấp 2 nên không thấy. Nay `PetPolicy.petBonus` (cùng công thức, không chặn), test hồi quy tới cấp 30.
