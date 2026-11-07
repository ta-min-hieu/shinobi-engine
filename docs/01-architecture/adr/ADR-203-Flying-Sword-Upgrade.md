# ADR-203: Nâng cấp phi kiếm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Nâng cấp phi kiếm" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: phi kiếm có cấp 1–5, dùng vật phẩm để lên cấp, mỗi cấp tăng thêm tốc độ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-070](ADR-070-Flying-Sword-Mount.md) (phi kiếm, ô MOUNT) · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (cường hoá — loại ô MOUNT) · [ADR-201](ADR-201-Spirit-Beast-Food.md) |

---

## Decision

**D1 — Cấp lưu ở `enhance_level` của dòng phi kiếm**: cường hoá thường đã loại ô MOUNT (`EnhanceItemUseCase`), nên cột
đó luôn 0 với phi kiếm — dùng làm "cấp phi kiếm", không cần bảng / cột mới. Cấp đi theo món khi cất kho, giao dịch,
thư (cùng đường của món cường hoá). Hệ quả có chủ đích: phi kiếm đã nâng là "món cường hoá" nên không bày phường thị
được (ADR-063 chỉ nhận món chưa cường hoá).

**D2 — Tốc độ**: `MountPolicy.speed(gốc, cấp) = min(100, gốc + 5 × cấp)`, cấp 0..5. Kiếm Gỗ 20% → 45% ở cấp 5; Ngọc Kiếm
35% → 60%. Tính ở `ComputeEquipmentBonusUseCase.moveSpeedPercent` (đường duy nhất ra tốc độ phi kiếm).

**D3 — Nâng bằng vật phẩm**: hiệu ứng dùng mới `UPGRADE_MOUNT` — **Phi Kiếm Tinh Thạch** (V158, 6.000 linh thạch ở Tàng
Bảo Các). Dùng ⇒ phi kiếm ĐANG MẶC +1 cấp, ghi trên chính aggregate túi đồ đang khoá (lưu cùng lúc với việc trừ tinh
thạch). Không cưỡi ⇒ `NO_MOUNT_EQUIPPED`; cấp 5 ⇒ `MOUNT_AT_MAX_LEVEL`; cả hai không trừ món. Thành công thì gateway gửi
lại tốc độ (đường tính lại sau khi mặc đồ). 100% thành công, không rơi cấp.

**D4 — Giao thức, chỉ thêm**: `ItemUseEffect.ITEM_USE_EFFECT_UPGRADE_MOUNT = 6`; lỗi qua `error_key`. Không opcode mới.
Client: nút "Dùng" sẵn có; phi kiếm hiện "+N" như món cường hoá.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cấp phi kiếm = `enhance_level`, +5%/cấp tới cấp 5 (trần 100%), nâng bằng Phi Kiếm Tinh Thạch (V158) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): `MountPolicy`, `UPGRADE_MOUNT`, `UseItemUseCase.upgradeWornMount`,
   `CompanionPort.wornMountItemId`, V158.
2. ✅ Test: `CompanionIntegrationTest.essenceShouldUpgradeTheWornSword_upToTheCap` (Postgres thật).
3. ✅ Kiểm sống gameProbe 2026-11-07 (V158): ĐạoHữu01 cưỡi Kiếm Gỗ (dòng thử) ⇒ bảng nhân vật `tốcĐộ+20%`; dùng một Phi
   Kiếm Tinh Thạch ⇒ 513 OK + 310 tốc độ mới gửi ngay, `enhance_level` của phi kiếm 0 → 1, tinh thạch 2 → 1; bảng nhân vật
   lần sau `tốcĐộ+25%`. Dòng thử đã gỡ.
4. Chờ chủ dự án: +5%/cấp, cấp tối đa 5, giá 6.000 là số đề xuất.
