# ADR-177: Lịch sử phường thị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-06) — chủ dự án chọn "Lịch sử phường thị" trong lượt hỏi lô 31 (AskUserQuestion; mô tả kèm: xem những món mình đã bán/mua ở phường thị: giá, lúc nào, với ai). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-063](ADR-063-Market.md) (phường thị, phí 5%) · [ADR-169](ADR-169-Gold-Ledger-History.md) (sổ vàng — cùng khuôn cửa sổ) |

---

## Decision

**D1 — Nguồn dữ liệu có sẵn**: `market_listing` (V65) đã giữ `seller_id`, `buyer_id`, `closed_at` và `status` ⇒ lịch sử
là một truy vấn đọc, KHÔNG thêm cột/bảng. Chỉ dòng `SOLD` (giao dịch đã chốt) mà mình là người bán HOẶC người mua;
dòng huỷ/hết hạn không phải giao dịch. **30 ngày, tối đa 50 dòng**, mới nhất trước.

**D2 — Chỉ mục (V141)**: hai chỉ mục một phần `(seller_id, closed_at DESC) WHERE status='SOLD'` và tương tự cho
`buyer_id` — truy vấn là `UNION ALL` hai nhánh để mỗi nhánh dùng đúng chỉ mục của mình. Không đổi dữ liệu.

**D3 — Số vàng**: bán ⇒ `+MarketPolicy.proceedsOf(price)` (đã trừ phí 5%, đúng số đã vào túi); mua ⇒ `-price`. Server
tính, client chỉ in.

**D4 — Giao thức, chỉ thêm**: `GetMarketHistoryRequest{}` / `GetMarketHistoryResponse{entries}` 1029/1030 (người hỏi
lấy từ session). Mỗi dòng: bán/mua, món, số lượng, giá, số vàng đổi, tên người kia ("" nếu nhân vật không còn), lúc chốt.

**D5 — Client**: cửa sổ mở bằng lệnh chat `/lichsupt` (hoặc `/markethistory`), cùng khuôn sổ vàng.

**Ngoài phạm vi**: lệnh cầu mua (ADR-116) — `market_buy_order` không ghi ai đã bán vào lệnh nên không dựng được "với ai";
xem ở sổ vàng (`/sovang`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đọc `market_listing` SOLD (bán + mua), 30 ngày / 50 dòng, chỉ mục V141, 1029/1030, `/lichsupt` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-06 (lô 31): V141, `MarketHistoryPort`/`MarketHistoryFacade`, `PostgresMarketHistoryAdapter`,
   `Lot31Commands.marketHistory` 1029/1030, `MarketHistoryCoordinator` `/lichsupt`.
2. ✅ Test: `MarketHistoryIntegrationTest` (Postgres thật: bán + mua, bỏ dòng huỷ và dòng quá 30 ngày, thứ tự),
   `MarketHistoryCoordinatorTest` (dòng bán/mua).
3. ✅ Kiểm sống gameProbe 2026-11-06 (bước `marketHistory`): ĐạoHữu01 treo kiếm gỗ giá 50, BaoDo01 mua (−50 vàng) ⇒
   ĐạoHữu01 thấy "BÁN kiếm gỗ x1 +48 với BaoDo01" (đã trừ phí 2), BaoDo01 thấy "MUA kiếm gỗ x1 −50 với ĐạoHữu01".
