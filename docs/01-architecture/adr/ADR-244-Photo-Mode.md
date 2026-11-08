# ADR-244: Chế độ chụp ảnh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Chế độ chụp ảnh" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: một phím ẩn toàn bộ HUD / khung chat để chụp màn hình đẹp). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-190](ADR-190-Key-Rebinding.md) (đổi phím tắt — phím này chưa đưa vào bảng đổi phím) |

---

## Decision

**D1 — Phím F10** (hoặc lệnh `/chupanh`, `/photo`) bật/tắt. Lúc bật: `WorldScreen` vẫn vẽ thế giới, VFX, lớp phủ
(thời tiết, đêm, viền máu thấp) nhưng **bỏ qua `syncCombatHud` + `panels.render`** — không HUD, không khung chat, không
cửa sổ. Thế giới vẫn chạy, mạng vẫn nhận gói. Bật thì một dòng chat nhắc "F10 để hiện lại" (hiện khi tắt).

**D2 — Phím đọc qua `KeyPressEdge`** như phím skill: đang gõ chữ trong ô nhập thì F10 không bật; lúc đang ẩn thì không
có ô nhập nào nên F10 luôn được tính. F10 cố định, chưa vào bảng đổi phím ADR-190 (thêm sau nếu cần — thuần client).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | F10 / `/chupanh` ẩn toàn bộ HUD + chat (bỏ `panels.render`), thế giới vẫn vẽ; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `PhotoModeCoordinator`, `WorldScreen.render`.
2. ✅ Test: logic bật/tắt dùng lại `KeyPressEdge` (đã có `KeyPressEdgeTest`); phần vẽ không kiểm được tự động.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
