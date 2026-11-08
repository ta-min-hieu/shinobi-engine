# ADR-233: Lịch sử cường hoá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Lịch sử cường hoá" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: xem lại các lần cường hoá gần đây, thành hay bại, tốn bao nhiêu). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (cường hoá) · [ADR-169](ADR-169-Gold-Ledger-History.md) (sổ vàng — cùng kiểu đọc audit) |

---

## Decision

**D1 — Đọc audit, không bảng mới.** Mỗi lần cường hoá đã ghi audit `ENHANCE_ITEM` với `{itemId, fromLevel, toLevel,
goldCost, result}` từ trước. `GetEnhanceHistoryRequest{}` / `GetEnhanceHistoryResponse{repeated EnhanceHistoryEntry}`
564/565 trả **20 dòng mới nhất** của chính mình. Không migration.

**D2 — Mẫu vật phẩm** lấy bằng nối `inventory_item` theo `itemId`; món đã bán/huỷ/phân giải ⇒ `item_template_id = 0`
(client ghi "món không còn"). Không ghi thêm mẫu vào audit cũ — dữ liệu cũ vẫn đọc được.

**D3 — Client** `/lichsucuonghoa` (hoặc `/enhancelog`) in từng dòng vào khung chat: mẫu, +từ → +tới, vàng, kết quả
(thành công / thất bại / thất bại tụt cấp).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 564/565 đọc audit `ENHANCE_ITEM`, 20 dòng; mẫu nối từ túi (0 nếu món đã mất); `/lichsucuonghoa` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): `ItemHistoryPort.enhanceHistory`, `PostgresItemHistoryAdapter`, `Lot38Commands.enhanceHistory`, `EnhanceHistoryCoordinator`.
2. ✅ Test: `Lot38IntegrationTest` (đọc JSON audit + nối mẫu), `Lot38CoordinatorsTest` (phân loại kết quả).
3. ✅ Kiểm sống gameProbe 2026-11-08: dòng audit `ENHANCE_ITEM` thử ⇒ `cườngHoá= 2:0>1/SUCCESS` (mẫu 2 = kiếm gỗ, nối đúng từ túi).
