# ADR-160: Sắp xếp túi đồ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "Sắp xếp túi đồ" trong lượt hỏi lô 29 (AskUserQuestion; mô tả kèm: một nút gom các chồng cùng loại và xếp lại túi theo loại → độ hiếm → tên. Server sắp xếp, client chỉ hiển thị). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-159](ADR-159-Item-Lock.md) (khoá — không gom chồng khoá với chồng thường) · V27 (gộp chồng một lần + cột `slot_index`) |

---

## Decision

**D1 — Thứ tự (luật thuần `BagSortPolicy`, feature-inventory/domain)**: trang bị (vũ khí → giáp → mũ → găng → giày →
ngọc bội 1/2 → pháp bảo → phi kiếm → linh thú; cấp yêu cầu cao trước; cường hoá cao trước) → vật phẩm DÙNG được (có
`use_effect`) → nguyên liệu/khác → món đang mặc cuối cùng. Cùng chỗ thì theo mã vật phẩm, chồng nhiều trước, id cuối
cùng (tất định). ⚠️ Lệch mô tả lúc hỏi: **dự án không có cột "độ hiếm"** và tên hiển thị nằm ở bundle i18n (server
không có) ⇒ "độ hiếm" thay bằng cấp yêu cầu + cường hoá, "tên" thay bằng mã vật phẩm.

**D2 — Gom chồng**: dòng gộp được (chưa cường hoá, không khảm/tẩy luyện/linh thú, không đang mặc, không đang chào giao
dịch — tức không ai tham chiếu theo id) cùng loại và cùng trạng thái khoá dồn vào dòng id nhỏ nhất, mỗi dòng tới
`max_stack`; dòng rỗng bị xoá. Một transaction, mở đầu `CharacterInventoryLock`, khoá mọi dòng túi; audit `BAG_SORTED`.

**D3 — Thứ tự được ĐỌC**: trước đây `slot_index` chỉ được ghi, danh sách túi luôn theo id. Nay
`PostgresInventoryAdapter` liệt kê `ORDER BY slot_index, id`; hai đường chèn dòng thô (`InventoryStackMerge`, giao
dịch) ghi `MAX(slot_index) + 1` thay cho "số dòng đang có" để dòng mới luôn nối CUỐI túi (đếm dòng cũ nhỏ hơn slot cuối
khi có đồ đang mặc hay lỗ sau khi xoá ⇒ dòng mới chen giữa túi). Túi chưa từng sắp: thứ tự cũ giữ nguyên trong thực tế
vì mọi đường ghi vốn đã nối cuối.

**D4 — Giao thức, chỉ thêm**: `SortInventoryCommand` (rỗng) / `SortInventoryResponse{ok, merged_rows}` — 530/531. Không
cổng PIN (không có gì rời túi).

**D5 — Client**: nút **Sắp xếp** ở hàng nút dưới cùng của túi đồ (hàng nút nay tách khỏi dòng chữ gợi ý — 4 nút);
kết quả về `BagActionsCoordinator` (đổi tên từ `ItemLockCoordinator`): dòng hệ thống + hỏi lại túi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Server gom + xếp theo luật thuần; danh sách túi đọc theo slot_index; dòng mới nối cuối; 530/531 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): `BagSortPolicy`, `BagSortPort`/`BagSortFacade`, `PostgresBagSortAdapter`, V-không
   (không đổi schema), 530/531, nút Sắp xếp.
2. ✅ Test: `BagSortPolicyTest` (4 ô), `BagSortIntegrationTest` (Postgres thật: gom, thứ tự đọc lại đúng, dòng mới nối
   cuối, chồng khoá không bị gom).
3. ✅ Kiểm sống gameProbe 2026-11-03 (`--lot21=bag,sortBag,bag`): thêm chồng đan 3 ở đầu túi ⇒ `gom=1`, túi thành
   `5:2x1 13:20x8 10:77x1 8:3x4 4:1x2 3:76x1 9:48x3 2:75x1` (kiếm → đan dùng được → còn lại theo mã).
4. Chờ chủ dự án: thứ tự D1 có đúng ý không (đặc biệt: không có "độ hiếm"); duyệt chữ `bag_sort.*`.
