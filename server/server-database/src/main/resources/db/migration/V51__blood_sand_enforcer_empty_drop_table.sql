-- V51 — `blood_sand_enforcer` có bảng rơi RỖNG mang mã của chính nó. Chỉ dữ liệu, không đổi schema,
-- không đổi những gì người chơi nhận được: hắn vẫn KHÔNG rơi gì, đúng như V41/V42 thiết kế.
--
-- VÌ SAO: đường chạy thật khi giết quái (`ConnectedCharacterRegistry.onMonsterKilled` →
-- `InventoryFacade.grantLoot(characterId, MÃ LOÀI)`) tra `drop_table.code` bằng MÃ LOÀI. V41 mang
-- chấp sự vào `monster_template` mà không chèn dòng `drop_table` nào, nên lần đầu mỗi phiên server
-- có người hạ hắn, `PostgresDropTableAdapter` rơi vào nhánh WARN "Không có bảng rơi nào mang mã ...
-- thường là gõ sai mã hoặc thiếu migration seed" — một báo động GIẢ cho một thiết kế CỐ Ý.
--
-- V33 đã chốt quy ước cho đúng trường hợp này (`trial_construct`): loài cố ý không rơi gì vẫn có MỘT
-- dòng `drop_table` rỗng, để adapter nói đúng câu INFO "cố ý không rơi gì", và khoá ngoại
-- `drop_table_id` trỏ vào bảng rỗng đó cho dữ liệu nói cùng một câu ở cả hai đường. File này áp
-- đúng quy ước ấy, không chọn gì mới.
--
-- Cổng giữ nó không quay lại: `ContentReferenceIntegrationTest.everyMonsterHasALootTableUnderItsOwnCode`.
INSERT INTO drop_table (code) VALUES ('blood_sand_enforcer');

UPDATE monster_template
   SET drop_table_id = (SELECT id FROM drop_table WHERE code = 'blood_sand_enforcer')
 WHERE code = 'blood_sand_enforcer';
