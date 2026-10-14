-- V53 — `sample_boss` trỏ `drop_table_id` về bảng rơi mang mã của chính nó. Chỉ dữ liệu, không đổi
-- schema, không đổi những gì người chơi nhận được (chủ dự án chọn 2026-10-14, docs/README.md dòng 34(iii)).
--
-- VÌ SAO: V17 tạo `drop_table` 'sample_boss' nhưng V41 mang boss vào `monster_template` với
-- `drop_table_id` NULL. Đường chạy thật (`InventoryFacade.grantLoot`) tra bảng rơi theo MÃ LOÀI nên
-- boss vẫn rơi đồ đúng — nhưng cột khoá ngoại nói "không có bảng rơi" trong khi bảng có thật: hai
-- đường đọc nói hai câu khác nhau, đúng thứ V51 đã dọn cho `blood_sand_enforcer`.
--
-- Cổng giữ nó không quay lại: `ContentReferenceIntegrationTest.everyMonsterHasALootTableUnderItsOwnCode`
-- (nay đòi cả `drop_table_id` KHÁC NULL).
UPDATE monster_template
   SET drop_table_id = (SELECT id FROM drop_table WHERE code = 'sample_boss')
 WHERE code = 'sample_boss'
   AND drop_table_id IS NULL;
