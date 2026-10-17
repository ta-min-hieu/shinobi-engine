-- V55 — hai vật quest rơi 100% nay BÁN ĐƯỢC cho cửa hàng, nhưng cửa hàng KHÔNG bán chúng ra
-- (chủ dự án duyệt 2026-10-17, docs/README.md dòng 65(b)). CHỈ THÊM DỮ LIỆU — không đổi schema.
--
-- VÌ SAO: `outer_sect_tally` (V38, quest 6) và `blood_sand_talisman` (V40) rơi 100% từ loài của quest
-- MÃI MÃI, kể cả sau khi quest đã xong — rơi 100% là cố ý (xem V38 mục CÂN BẰNG), nên thứ bị hỏng
-- là đầu ra: vật không bán được thì thành rác chiếm ô túi.
--
-- LUẬT MỚI CỦA `shop_item`: `buy_price = 0` nghĩa là "cửa hàng chỉ THU MUA, không bán ra"
-- (`ShopListing#soldByShop`). Không có luật này thì một dòng `shop_item` cho vật quest cũng là đường
-- MUA nó — mua 2 thẻ bài là xong quest 6 mà không đánh trận nào. Chọn ý nghĩa cho số 0 thay vì thêm
-- cột: `CHECK (buy_price >= 0)` của V8 vốn cho phép 0, và không dòng nào đang dùng 0 (bất biến V21
-- "giá mua > giá bán" loại trừ nó), nên luật này không đổi nghĩa một dòng có sẵn nào.
--
-- GIÁ (placeholder cân bằng, cùng thang V21 — chủ dự án chỉnh được bằng một UPDATE):
--   * thẻ bài 10 — dưới `iron_scrap` (12), vì nó chỉ là tấm thẻ gỗ của đệ tử ngoại môn.
--   * bùa Huyết Sa 15 — vùng sau, quái mạnh hơn, nên nhỉnh hơn một chút.
-- Bán thì không in vàng: không có đường mua vào, nên không có vòng mua-bán nào để lặp.
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 0, 10 FROM item_template WHERE code = 'outer_sect_tally'
 AND NOT EXISTS (SELECT 1 FROM shop_item s WHERE s.item_template_id = item_template.id);

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 0, 15 FROM item_template WHERE code = 'blood_sand_talisman'
 AND NOT EXISTS (SELECT 1 FROM shop_item s WHERE s.item_template_id = item_template.id);
