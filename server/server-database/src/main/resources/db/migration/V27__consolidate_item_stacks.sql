-- V27 — dồn các chồng vật phẩm bị phân mảnh TRƯỚC khi có luật xếp chồng (2026-09-05).
--
-- Bối cảnh: `item_template.max_stack` có từ đầu và ĐÚNG (trang bị = 1, tiêu hao/nguyên liệu/vật
-- phẩm nhiệm vụ = 99), nhưng KHÔNG code nào đọc nó — mọi lần nhặt/mua/nhận thưởng đều mở một ô túi
-- mới. Một nhân vật test giữ 17 phi đao trong 5 dòng `inventory_item` riêng. Bản sửa cùng ngày
-- (`Inventory.add` + `InventoryStackMerge`) chỉ áp cho đồ MỚI; những dòng đã nằm sẵn trong DB thì
-- không có gì tự dồn chúng lại. Migration này làm việc đó đúng một lần.
--
-- ⚠️ CHỈ đụng vật phẩm XẾP CHỒNG ĐƯỢC (`max_stack > 1`). Mọi thứ có `equip_slot` đều `max_stack = 1`
-- nên KHÔNG bao giờ vào đây — quan trọng, vì `equipment_slot.inventory_item_id` trỏ vào bảng này:
-- gộp nhầm một món đang mặc sẽ xoá mất dòng nó đang tham chiếu (FK là ON DELETE SET NULL, tức người
-- chơi lặng lẽ mất món đồ đang mặc). Điều kiện `max_stack > 1` là thứ chặn chuyện đó.
--
-- ⚠️ KHÔNG XÉ LẺ, đúng như code: chỉ dồn khi TỔNG của cả nhóm vẫn ≤ `max_stack`. Nhóm nào cộng lại
-- vượt trần thì để nguyên, không tách thành "một chồng đầy + phần dư" — cùng lý do đã ghi ở javadoc
-- `Inventory.add`: giữ đúng một vị trí cho mỗi lần cộng thì không ai phải ghép mảnh lại. Với dữ liệu
-- hiện tại (trần 99, các chồng lẻ 1-5 cái) không nhóm nào chạm trần.
--
-- `enhance_level` nằm trong khoá nhóm: một món +7 không phải cùng thứ với +0. Thực tế chỉ trang bị
-- mới cường hoá được, mà trang bị thì không vào đây — điều kiện này là để ĐÚNG NGHĨA, không dựa vào
-- sự trùng hợp đó.
--
-- Dòng SỐNG SÓT là dòng có `id` NHỎ NHẤT của nhóm: tất định, và giữ lại `slot_index` cũ nên thứ tự
-- hiển thị trong túi không nhảy lung tung. `slot_index` sau đó có lỗ — chấp nhận được, cột này không
-- có ràng buộc duy nhất và adapter chỉ dùng nó để sắp xếp.
--
-- Idempotent: chạy lại lần hai thì `HAVING COUNT(*) > 1` không còn nhóm nào khớp, không làm gì.

-- ⚠️ PHẢI là MỘT câu lệnh duy nhất. Bản nháp đầu tách thành UPDATE rồi DELETE, và sai: DELETE tính
-- lại `SUM(quantity)` SAU KHI UPDATE đã ghi tổng vào dòng giữ lại, nên tổng bị đếm gần gấp đôi —
-- nhóm nào vượt trần sau khi đội lên sẽ KHÔNG bị xoá, để lại số lượng đã nhân đôi trong túi người
-- chơi. Trong data-modifying CTE của Postgres, mọi nhánh đọc CÙNG một snapshot, nên DELETE và UPDATE
-- ở đây đều nhìn trạng thái GỐC.

WITH mergeable AS (
    SELECT ii.character_id,
           ii.item_template_id,
           ii.enhance_level,
           MIN(ii.id)       AS keep_id,
           SUM(ii.quantity) AS merged_quantity
      FROM inventory_item ii
      JOIN item_template it ON it.id = ii.item_template_id
     WHERE it.max_stack > 1
     GROUP BY ii.character_id, ii.item_template_id, ii.enhance_level, it.max_stack
    HAVING COUNT(*) > 1
       AND SUM(ii.quantity) <= it.max_stack
),
removed AS (
    DELETE FROM inventory_item ii
     USING mergeable m
     WHERE ii.character_id     = m.character_id
       AND ii.item_template_id = m.item_template_id
       AND ii.enhance_level    = m.enhance_level
       AND ii.id              <> m.keep_id
    RETURNING ii.id
)
UPDATE inventory_item ii
   SET quantity = m.merged_quantity
  FROM mergeable m
 WHERE ii.id = m.keep_id;
