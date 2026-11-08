# ADR-232: Phân giải hàng loạt

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Phân giải hàng loạt" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: phân giải mọi trang bị dưới một cấp trong một lệnh). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-097](ADR-097-Dismantle-Equipment.md) (phân giải từng món) · [ADR-159](ADR-159-Item-Lock.md) · [ADR-170](ADR-170-Sell-Junk.md) (bán nhanh đồ rác) |

---

## Decision

**D1 — Một lệnh, nhiều lần phân giải thường.** `BulkDismantleCommand{max_required_level}` / `BulkDismantleResponse{dismantled,
gold, herbs}` 562/563. Server liệt kê ứng viên rồi gọi **đúng** `DismantleFacade.dismantle` (ADR-097) cho từng món —
cùng công thức thưởng, cùng kiểm tra dưới khoá, cùng audit. Không có đường thưởng thứ hai để lệch.

**D2 — Ứng viên.** Trang bị (`equip_slot` có giá trị) có cấp yêu cầu ≤ ngưỡng, **không khoá**, **không đang mặc**,
**không khảm ngọc** (ngọc là tài sản riêng, tháo trước — ADR-168), không đang chào giao dịch; theo thứ tự ô túi. Tối đa
**40 món/lệnh** (một túi đầy cỡ thường) để một lệnh không giữ Virtual Thread quá lâu.

**D3 — Client** `/phangiai <cấp>` (hoặc `/dismantleall`), cấp 1–100. Báo tổng số món, vàng, Linh Thảo rồi hỏi lại túi.
Không có bước xác nhận: điều kiện đã loại mọi thứ người chơi có thể còn quý (khoá/đang mặc/có ngọc).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 562/563 gọi lại `DismantleFacade` từng món; loại khoá/mặc/khảm ngọc/giao dịch; tối đa 40; `/phangiai <cấp>` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): `ItemHistoryPort.dismantleCandidates`, `ItemHistoryFacade.dismantleAll`, `Lot38Commands.bulkDismantle`, `BulkDismantleCoordinator`.
2. ✅ Test: `Lot38IntegrationTest.listsDismantleCandidatesAndReadsEnhanceHistory` (loại món khoá, món không phải trang bị, ngưỡng cấp), `Lot38CoordinatorsTest` (đọc cấp).
3. ✅ Kiểm sống gameProbe 2026-11-08: `dismantleAll:1` ⇒ `phânGiải=1 vàng=20 thảo=1` (đúng một thanh kiếm gỗ thừa; kiếm đang mặc và món khoá không bị đụng); gọi lại ⇒ 0. Dữ liệu thử đã hoàn lại.
