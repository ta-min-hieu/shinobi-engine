# ADR-201: Cho linh thú ăn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Cho linh thú ăn" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: dùng Linh Thú Thực để linh thú đang mặc lên kinh nghiệm, không phải đi đánh quái). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-072](ADR-072-Spirit-Pets.md) (cấp linh thú) · [ADR-078](ADR-078-Pet-Evolution.md) (hiệu ứng dùng `EVOLVE_PET`) · [ADR-046](ADR-046-Health-Recovery.md) (đường dùng vật phẩm) |

---

## Decision

**D1 — Một hiệu ứng dùng mới**: `ItemUseEffect.FEED_PET` — dùng ⇒ linh thú ĐANG MẶC nhận `use_value` EXP, qua cấp bằng
đúng `PetPolicy.gain` của EXP đánh quái (tới cấp 30). Không mặc linh thú ⇒ `NO_PET_EQUIPPED`; đã cấp tối đa ⇒
`PET_AT_MAX_LEVEL` (mới). Cả hai KHÔNG trừ món.

**D2 — Một transaction**: khoá túi đồ → khoá dòng `pet_state` → ghi EXP → trừ món (thêm
`CompanionPort.grantPetExp(tx, …)` chạy trong transaction của người gọi). Xong thì gateway tính lại chỉ số trang bị như
sau khi tiến hoá (cấp linh thú đổi chỉ số).

**D3 — Món + giá (đề xuất, Điều 27)**: V156 thêm **Linh Thú Thực** (`spirit_beast_food`), 200 EXP / cái, bán ở Tàng Bảo
Các 600 linh thạch (thu lại 60). Con cấp 1 → 10 cần 4.500 EXP ≈ 23 cái.

**D4 — Giao thức, chỉ thêm**: `ItemUseEffect.ITEM_USE_EFFECT_FEED_PET = 5`; lỗi đi qua `error_key` có sẵn
(`error.pet_at_max_level`). Không opcode mới. Client: nút "Dùng" sẵn có trong túi đồ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Hiệu ứng `FEED_PET`, EXP trong cùng transaction với việc trừ món, V156 Linh Thú Thực 200 EXP / 600 linh thạch | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): `FEED_PET`, `UseItemUseCase.feedEquippedPet`, `CompanionPort.grantPetExp(tx, …)`, V156.
2. ✅ Test: `PetEvolutionUseItemTest.foodShouldFeedTheEquippedPet_orRefuseWithoutConsuming`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (V156): ĐạoHữu01 mặc Hồ Ly cấp 1 (dòng thử), dùng một Linh Thú Thực ⇒ 513 OK,
   `pet_state` = cấp 2 dư 100 EXP (200 EXP, cấp 1 cần 100), túi 3 → 2 cái; lần dùng thứ hai ngay sau đó bị chặn bởi hồi
   chiêu dùng vật phẩm sẵn có. Dòng thử đã gỡ.
4. ✅ SpotBugs bắt bỏ qua kết quả `grantPetExp` — nay rỗng (linh thú vừa bị tháo) ⇒ `NO_PET_EQUIPPED`, không trừ món.
5. Chờ chủ dự án: 200 EXP / 600 linh thạch là số đề xuất.
