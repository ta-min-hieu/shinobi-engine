# ADR-234: Giá gợi ý phường thị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Giá gợi ý phường thị" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: khi rao bán thấy giá rẻ nhất đang rao và giá trung bình gần đây). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-063](ADR-063-Market.md) (phường thị) · [ADR-177](ADR-177-Market-History.md) (lịch sử phường thị) |

---

## Decision

**D1 — Một truy vấn gộp.** `GetMarketPriceHintRequest{item_template_id}` / `GetMarketPriceHintResponse{item_template_id,
lowest_active, average_sold, sold_listings}` 1040/1041 (dải Market). Tất cả tính theo **giá MỘT cái** (`price /
quantity`): rẻ nhất trong các dòng `ACTIVE`; trung bình và số dòng `SOLD` có `closed_at` trong **7 ngày** qua. Không có
dữ liệu ⇒ 0.

**D2 — Thông tin công khai.** Ai cũng xem được bảng rao, nên không lộ gì thêm; không kèm tên người bán/mua. Chỉ là
gợi ý: giá rao vẫn do người bán tự nhập, server không ép.

**D3 — Client** chọn món trong túi → `/giacho` (hoặc `/pricecheck`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 1040/1041 giá một cái: rẻ nhất đang rao + trung bình đã bán 7 ngày + số lượt; `/giacho` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): `MarketPriceHintPort`/`MarketPriceHintFacade`, `PostgresMarketPriceHintAdapter`, `Lot38Commands.priceHint`, `MarketPriceHintCoordinator`.
2. ✅ Test: `Lot38IntegrationTest.aggregatesMarketPricePerPiece` (chia theo số lượng, bỏ dòng bán quá 7 ngày).
3. ✅ Kiểm sống gameProbe 2026-11-08: một dòng rao 5 cái/250 + một dòng đã bán 2 cái/60 ⇒ `rẻNhất=50 tbBán=30 lượt=1` (tính theo giá một cái). Dữ liệu thử đã xoá.
