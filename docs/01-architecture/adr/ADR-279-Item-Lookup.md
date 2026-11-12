# ADR-279: Tra cứu vật phẩm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Tra cứu vật phẩm" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: /vatpham <tên>: chỉ số, cấp yêu cầu, giá bán và những loại quái rơi ra món đó). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-255](ADR-255-Monster-Info.md) (chiều ngược: quái rơi gì) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (catalog vật phẩm) |

---

## Decision

**D1 — Chỉ số lấy từ catalog client đã có** (509/510); client tìm mẫu theo **tên hiển thị** (không dấu, trùng khít ưu tiên rồi
chứa-trong) — đây là chỗ client có bundle tên, khác ADR-255 phải tra theo mã. Server chỉ bổ sung thứ catalog không có:
`LookupItemRequest{item_template_id}` / `LookupItemResponse{item_template_id, buy_price, sell_price, repeated
ItemDropSourceView{monster_code, monster_name_key, chance_percent}}` 572/573 (dải Inventory) — giá `shop_item` và các
loại quái có món trong bảng rơi (`drop_table_entry` ⋈ `monster_template`, tỉ lệ cao nhất mỗi quái). Thông tin công
khai, chỉ đọc. Client `/vatpham <tên>` (hoặc `/item`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client tìm theo tên trong catalog; 572/573 trả giá cửa hàng + quái rơi; `/vatpham` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `ItemLookupPort`/`ItemLookupFacade`, `PostgresItemLookupAdapter`, `Lot44Commands.lookupItem`, `ItemLookupCoordinator`.
2. ✅ Test: `Lot44IntegrationTest.looksUpPricesAndDropSources`.
3. ✅ Kiểm sống gameProbe 2026-11-12 (`lookupItem:20`): mua=80 thu=20, 14 nguồn rơi (`ancient_stone_colossus@100%` … `blood_sand_acolyte@40%`); `lookupItem:42` (kunai, không quái nào rơi) ⇒ mua=12000 thu=1200, danh sách rơi rỗng.
