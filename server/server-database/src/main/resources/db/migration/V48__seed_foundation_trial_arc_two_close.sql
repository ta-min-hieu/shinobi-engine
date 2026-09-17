-- V48 — quãng Lv28–36 của arc 2, và là nhịp ĐÓNG arc: "Trúc Cơ thành công".
--
-- ĐÂY LÀ NỘI DUNG CUỐI CÙNG CỦA v1 (chủ dự án chốt 2026-09-16: hoàn thiện một bản HOÀN CHỈNH sớm
-- nhất có thể, arc 3–6 phát triển thêm sau). Xem Exit Criteria v1 ở ROADMAP §Phase 10.
--
-- =================================================================================================
-- VÌ SAO DỪNG Ở ĐÚNG CẤP 36, VÀ VÌ SAO ĐÓ KHÔNG PHẢI MỘT CON SỐ TIỆN TAY
-- =================================================================================================
-- `CultivationRealm.of(level)` chia năm cảnh giới đầu theo 18 cấp một cảnh giới, hai cấp một tầng:
--     realmIndex = (level − 1) / 18 ;  tier = ((level − realmIndex×18) + 1) / 2
-- Ở Cấp 36: realmIndex = 35/18 = 1 ⇒ **Trúc Cơ**, levelInRealm = 18 ⇒ **tầng 9** — tầng CUỐI.
-- Ở Cấp 37: realmIndex = 2 ⇒ Tử Phủ, khớp đúng STORY §6 "Arc 3 — Nội Môn (Tử Phủ, Lv37–54)".
-- ⇒ Mốc "Trúc Cơ thành công" là có thật về CƠ CHẾ, không chỉ là lời kể. Người chơi kết thúc v1 ở
--   ranh giới cảnh giới chứ không phải ở chỗ hết việc.
--
-- =================================================================================================
-- BỘ "TRÚC CƠ" — vì sao phải có một bậc trang bị mới, không phải chỉ thêm quái
-- =================================================================================================
-- Playtest 2026-09-16 đo được một trần thật: bộ "Ngoại Môn" (Lv15) ĐÃ mạnh hơn thang quái hiện có —
-- người chơi hạ đầu lĩnh arc 1 với biên 5 nhát. Sức người chơi đứng yên từ Lv15 nghĩa là quái mạnh
-- lên chỉ làm BIÊN MỎNG DẦN, và trận biến thành tung đồng xu chứ không khó lên. Đó là cùng một cái
-- bẫy mà V47 đã trả giá (biên +1, thắng còn 8 máu và chỉ nhờ một chí mạng).
--
-- Bộ này vì thế là ĐIỀU KIỆN CẦN của hai quest cuối, đúng khuôn bộ Ngoại Môn với quest 11/12 (V45).
--
--   Tổng bộ Ngoại Môn (Lv15) : công 34 · thủ 30 · kháng 24 · máu  70
--   Tổng bộ Trúc Cơ  (Lv30) : công 52 · thủ 45 · kháng 36 · máu 106
--   ⇒ người chơi: công 64→82 · thủ 50→65 · máu 170→206
--
-- ⚠️ KHÔNG bán ở Tàng Bảo Các, cùng lý do V45 ghi cho bộ Ngoại Môn: bán nó là cho phép mua đường
--    tắt qua chính nhịp mà chuỗi quest dựng ra.
-- -------------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
SELECT * FROM (VALUES
        ('foundation_blade',    'item.foundation_blade.name',    1, 'WEAPON',      34,  0,  0,   0, 30),
        ('foundation_robe',     'item.foundation_robe.name',     1, 'ARMOR',        0, 18, 12,  40, 30),
        ('foundation_cap',      'item.foundation_cap.name',      1, 'HELMET',       0, 11,  8,  21, 30),
        ('foundation_gloves',   'item.foundation_gloves.name',   1, 'GLOVE',        6,  6,  3,  12, 30),
        ('foundation_boots',    'item.foundation_boots.name',    1, 'BOOT',         0,  8,  6,  12, 30),
        ('foundation_talisman', 'item.foundation_talisman.name', 1, 'ACCESSORY_1', 12,  2,  7,  21, 30)
    ) AS incoming (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = incoming.code);

-- -------------------------------------------------------------------------------------------------
-- 2. Bảng rơi đồ cho hai loài mới.
-- -------------------------------------------------------------------------------------------------
INSERT INTO drop_table (code)
SELECT 'wasteland_beast' WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'wasteland_beast');

INSERT INTO drop_table (code)
SELECT 'foundation_warden' WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'foundation_warden');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 50, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'wasteland_beast' AND it.code = 'minor_healing_pill'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 2, 3
  FROM drop_table dt, item_template it
 WHERE dt.code = 'foundation_warden' AND it.code = 'minor_healing_pill'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 35, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'foundation_warden' AND it.code = 'enhance_protection_charm'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

-- -------------------------------------------------------------------------------------------------
-- 3. Hai loài. Chỉ số CHỌN TỪ RÀNG BUỘC biên +2, tính trên người chơi ĐÃ mặc bộ Trúc Cơ
--    (công 82 · thủ 65 · máu 206). Công thức là `hit = round(atk × 100/(100+def))`, y như mọi
--    lớp cân bằng khác — không có số nào ở đây được chọn rồi mới đi kiểm.
--
--      yêu thú rìa Hoang Vực  280/44/32 :  người chơi ceil(280/62) = 5 nhát
--                                          quái     ceil(179/27) = 7 nhát   ⇒ biên +2
--      cỗ khảo hạch Trúc Cơ   300/46/35 :  người chơi ceil(300/61) = 5 nhát
--                                          quái     ceil(178/28) = 7 nhát   ⇒ biên +2
--
--    (179 và 178 là máu lúc tới nơi = 206 trừ ĐÚNG MỘT nhát phủ đầu — aggro 3 vẫn với tới người đi
--     ngang, đo được hai lần độc lập ở V47; đừng trừ 0 cho loài có aggro > tầm đánh 2m.)
--
--    ⚠️ Cả hai giữ `aggro_radius_meters = 3` và `retaliates = true`, cùng khuôn đám Huyết Sa.
-- -------------------------------------------------------------------------------------------------
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, aggro_radius_meters, retaliates)
SELECT 'wasteland_beast', 'hud.monster.wasteland_beast', 'mob_wasteland-beast', 29, 280, 44, 0, 32, 24,
       94, 520, (SELECT id FROM drop_table WHERE code = 'wasteland_beast'), 3, true
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'wasteland_beast');

INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, aggro_radius_meters, retaliates)
SELECT 'foundation_warden', 'hud.monster.foundation_warden', 'mob_foundation-warden', 33, 300, 46, 0, 35, 26,
       96, 700, (SELECT id FROM drop_table WHERE code = 'foundation_warden'), 3, true
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'foundation_warden');

-- -------------------------------------------------------------------------------------------------
-- 4. Ba điểm spawn: 175, 188, 201.
--
--    Luật hình học đã có ba số hạng, và lần này áp CẢ BA (bài học 2026-09-16 — chỗ nghỉ phải soi
--    theo leash, không theo aggro):
--      · cách quỷ đá @161: 175 − 161 = 14 > leash 10 + tầm đánh 2 = 12 ✓ và > aggro 6+3 = 9 ✓
--      · giữa các điểm mới: 13 > 12 ✓
--      · ba bệ nhảy [3,7] [9,13] [15,19] và vùng an toàn [-15, 5] — không điểm nào chạm tới ✓
--
--    ⚠️ `DemoPlatforms.GROUND_MAX_X` đi **170 → 210** CÙNG lượt (lần thứ TƯ). Nền đất là hằng số
--    client còn spawn sống trong DB, không có gì nối hai thứ ngoài luật "nền đất phải phủ hết
--    spawn" — đã bị vi phạm trong im lặng hai lần trước khi có test gác.
--    210 = 201 + 9m biên, đúng quy ước V40/V45/V47 tự đặt.
-- -------------------------------------------------------------------------------------------------
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 175, 0, 30 FROM monster_template mt
 WHERE mt.code = 'wasteland_beast'
   AND NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.monster_template_id = mt.id AND s.pos_x = 175);

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 188, 0, 30 FROM monster_template mt
 WHERE mt.code = 'wasteland_beast'
   AND NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.monster_template_id = mt.id AND s.pos_x = 188);

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 201, 0, 30 FROM monster_template mt
 WHERE mt.code = 'foundation_warden'
   AND NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.monster_template_id = mt.id AND s.pos_x = 201);

-- -------------------------------------------------------------------------------------------------
-- 5. Năm quest, nối tiếp `scatter_the_blood_sand_band` (nhịp cuối của V47).
--
--    Nội dung (số lần hạ, thứ tự bước, phần thưởng) nằm HẾT trong script (Điều 27); bảng này chỉ
--    giữ định danh + đường dẫn script + điều kiện MỞ.
--
--    EXP: Kagemaru đo được 38.850 ở Cấp 28; mốc Cấp 36 là 100 × (1+…+35) = 63.000 ⇒ cần 24.150.
--    Năm quest chia 4.400 / 4.600 / 4.800 / 5.000 / 5.400 = 24.200, cộng EXP quái rơi dọc đường.
--    ⚠️ Con số CUỐI (5.400) phải được CHỌN TỪ RÀNG BUỘC "lĩnh xong đứng đúng Cấp 36", và khoá lại
--    bằng ô test đối chiếu `ExperienceCurve` THẬT — đúng khuôn `QuestIntegrationTest` đã làm cho
--    `awakening_trial` (Cấp 10). KHÔNG chép lại công thức vào test.
--    📌 Ghi chú trong `awakening_trial.js` trỏ tới một lớp tên `AwakeningTrialProgressionTest` —
--    lớp đó KHÔNG tồn tại; chốt chặn thật nằm ở `QuestIntegrationTest`. Sửa luôn câu đó cùng đợt.
-- -------------------------------------------------------------------------------------------------
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'wasteland_edge_patrol', 'MAIN', '/scripts/quest/wasteland_edge_patrol.js',
       (SELECT id FROM quest_template WHERE code = 'scatter_the_blood_sand_band')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'wasteland_edge_patrol');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'rank_board_dispute', 'MAIN', '/scripts/quest/rank_board_dispute.js',
       (SELECT id FROM quest_template WHERE code = 'wasteland_edge_patrol')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'rank_board_dispute');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'foundation_pill_run', 'MAIN', '/scripts/quest/foundation_pill_run.js',
       (SELECT id FROM quest_template WHERE code = 'rank_board_dispute')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'foundation_pill_run');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'outer_sect_proving', 'MAIN', '/scripts/quest/outer_sect_proving.js',
       (SELECT id FROM quest_template WHERE code = 'foundation_pill_run')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'outer_sect_proving');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'foundation_breakthrough', 'MAIN', '/scripts/quest/foundation_breakthrough.js',
       (SELECT id FROM quest_template WHERE code = 'outer_sect_proving')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'foundation_breakthrough');
