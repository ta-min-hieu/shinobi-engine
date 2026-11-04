# ADR-171: Soi trang bị người khác

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Soi trang bị người khác" trong lượt hỏi lô 30 (AskUserQuestion; mô tả kèm: bấm vào người chơi khác xem cảnh giới, Lực Chiến, trang bị đang mặc (cường hoá, ngọc) — họ có thể tắt cho xem trong /caidat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-056](ADR-056-Character-Stat-Sheet-And-Combat-Power.md) (Lực Chiến — cùng công thức) · [ADR-143](ADR-143-Display-Settings.md) (`/caidat`) |

---

## Decision

**D1 — Soi theo tên**: `InspectCharacterRequest{name}` — cảnh giới (cấp), linh căn, **Lực Chiến** (đúng công thức bảng nhân
vật ADR-056, tính ở gateway), từng món đang mặc (ô, mẫu, cường hoá, ngọc khảm). Xem được cả người đang offline (đọc DB).
Chỉ đọc. ⚠️ Lệch mô tả lúc hỏi ("bấm vào người chơi"): bản này mở bằng lệnh chat `/soi <tên>` — client chưa có menu
chuột phải trên nhân vật khác; nút trên menu đó là việc sau.

**D2 — Riêng tư do SERVER giữ (V137)**: `character_inspect_privacy(character_id PK, hidden)`; không có dòng = cho xem.
Người đã tắt ⇒ `HIDDEN` (tự soi mình vẫn được). Công tắc nằm trong `/caidat` ("Cho người khác soi trang bị", mặc định
BẬT) như chủ dự án mô tả, nhưng vì người HỎI là người khác nên giá trị phải ở server: client gửi
`SetInspectPrivacyCommand` lúc vào game và mỗi lần đổi công tắc.

**D3 — Giao thức, chỉ thêm**: 249/250 soi, 251/252 công tắc. Ô trang bị là `int32` (giá trị `EquipmentSlot`) để
`character.proto` không phải import `inventory.proto`.

**D4 — Client**: cửa sổ soi — dòng đầu "Tên — Trúc Cơ 1 — Hoả — Lực Chiến 1.652", mỗi món "Pháp khí: Kiếm gỗ +3 [Hoả
Linh Ngọc]"; bị ẩn/không có tên ⇒ một dòng hệ thống.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Soi theo tên (cả offline), Lực Chiến cùng công thức ADR-056, riêng tư lưu ở server, công tắc trong /caidat; 249–252 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): V137, `InspectPrivacyPort`/`InspectPrivacyFacade`, `PostgresInspectPrivacyAdapter`,
   `InspectCommands` 249–252, `InspectCoordinator` `/soi`, `DisplaySettings.Option.ALLOW_INSPECT` + đồng bộ server.
2. ✅ Test: `InspectPrivacyIntegrationTest` (Postgres), `InspectCoordinatorTest`, `DisplaySettingsTest` vẫn xanh.
3. ✅ Kiểm sống gameProbe 2026-11-04: BaoDo01 soi ĐạoHữu01 ⇒ cấp 20, Lực Chiến 1652, pháp khí kiếm gỗ [Hoả Linh Ngọc];
   BaoDo01 bật ẩn ⇒ ĐạoHữu01 soi BaoDo01 ⇒ `HIDDEN`; tự soi vẫn OK; tên lạ ⇒ `NOT_FOUND`.
4. Việc sau: nút "Soi" trên menu chuột phải nhân vật khác (khi client có menu đó).
