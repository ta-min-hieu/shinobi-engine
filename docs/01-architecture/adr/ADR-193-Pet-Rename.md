# ADR-193: Đặt tên linh thú

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Đổi tên linh thú" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: đặt tên riêng cho linh thú, hiện trên đầu linh thú và trong bảng linh thú). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-072](ADR-072-Spirit-Pets.md) (linh thú, `pet_state`) · [ADR-078](ADR-078-Pet-Evolution.md) |

---

## Lệch so với mô tả lúc hỏi

Mô tả hứa tên "hiện trên đầu linh thú". **Linh thú hiện không có thực thể trong world** (ADR-072 chỉ là ô trang bị
cộng chỉ số, không theo chân người chơi), nên không có "đầu linh thú" để gắn tên. Tên hiện ở **bảng nhân vật** (dòng
Linh thú — nơi duy nhất client đang bày linh thú). Khi nào linh thú có hình trong world thì tên đi theo `PetView`
sẵn có.

## Decision

**D1 — Ai, cái gì**: linh thú **đang mặc** (ô PET). Miễn phí, đổi bao nhiêu lần cũng được; tên rỗng = xoá tên riêng.

**D2 — Luật tên**: 2..12 ký tự, chỉ chữ / số / khoảng trắng đơn (`PetNameFacade.isValid`); gateway chặn thêm từ cấm
bằng `ProfanityFilter` của feature-chat (cùng cách thông báo bang ADR-179). Tên linh thú KHÔNG cần duy nhất.

**D3 — Lưu**: V151 thêm `pet_state.custom_name VARCHAR(12)` — theo dòng túi đồ như cấp/bậc, nên tháo ra mặc lại vẫn giữ
tên, linh thú bị xoá thì tên đi theo. Linh thú chưa có dòng `pet_state` (chưa nhận EXP lần nào) ⇒ đặt tên tạo dòng cấp
1, đúng giá trị ngầm định cũ.

**D4 — Giao thức, chỉ thêm**: `RenamePetCommand{name}` / `RenamePetResponse{outcome, name}` 253/254;
`PetView.custom_name = 6`.

**D5 — Client**: lệnh chat `/tenlinhthu <tên>` (hoặc `/petname`); không kèm tên = xoá. Bảng nhân vật hiện
"Tiểu Hồ (Hồ Ly)".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tên riêng linh thú đang mặc, `pet_state.custom_name` (V151), 253/254, `PetView.custom_name` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): V151, `PetNamePort`/`PetNameFacade`, `PostgresPetNameAdapter`,
   `Lot33Commands.renamePet`, `PetNameCoordinator`.
2. ✅ Test: `PetNameFacadeTest`, `CompanionIntegrationTest.petNameShouldFollowTheWornPet` (Postgres thật),
   `PetNameCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `petName:<tên>`): ĐạoHữu01 chưa mặc linh thú ⇒ `NO_PET`; mặc Hồ Ly (dòng thử
   trong DB dev) ⇒ `petName:A` ⇒ `INVALID_NAME`, `petName:Tiểu Hồ` ⇒ `OK`, `pet_state` có dòng cấp 1 mang tên; bảng nhân
   vật (`--statSheet`) trả `custom_name: "Tiểu Hồ"`; `petName:` trống ⇒ `CLEARED`. Dòng thử đã gỡ.
