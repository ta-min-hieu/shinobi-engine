# ADR-257: Dự tính cường hoá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Dự tính cường hoá" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: chọn một món: xem giá vàng, tỉ lệ thành công, nguy cơ tụt cấp của lần cường hoá kế tiếp). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (cường hoá) · [ADR-233](ADR-233-Enhance-History.md) |

---

## Decision

**D1 — Đọc đúng bảng server.** `GetEnhancePreviewRequest{item_id}` / `GetEnhancePreviewResponse{outcome, current_level,
gold_cost, success_percent, drops_on_fail, protection_charms}` 568/569 (dải Inventory). Số lấy thẳng từ
`EnhancementTable` (giá, tỉ lệ, mốc tụt cấp) — cùng hàm lò cường hoá dùng, không có bản sao ở client để lệch. Cùng luật
"vào được lò": phải là trang bị, không phải phi kiếm/linh thú; đã tối đa ⇒ `MAX_LEVEL`. Kèm số Bảo Vệ Phù đang có.
Chỉ đọc, không roll, không trừ gì.

**D2 — Client** chọn món trong túi → `/dutinh` (hoặc `/enhanceinfo`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 568/569 đọc `EnhancementTable` cho món đang chọn (giá, tỉ lệ, tụt cấp, số Bảo Vệ Phù); `/dutinh` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `Lot41Commands.enhancePreview`, `EnhancePreviewCoordinator`.
2. ✅ Test: kiểm sống (bảng `EnhancementTable` đã có test riêng).
3. ✅ Kiểm sống gameProbe 2026-11-08: kiếm gỗ +0 ⇒ `OK` 100 vàng, 95%, không tụt, 5 Bảo Vệ Phù; đan dược ⇒ `NOT_EQUIPMENT`.
