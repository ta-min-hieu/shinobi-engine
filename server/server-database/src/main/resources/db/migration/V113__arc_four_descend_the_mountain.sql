-- V113 — ARC 4 "Hạ Sơn" (Kim Đan, Lv55–72), STORY.md §6 — ADR-139, lô 25.
--
-- Nối sau `repel_the_altar_master` (V109). Cấp 54 là trần Thiên Kiếp (ADR-126): người chơi phải độ kiếp lần ba mới vào
-- Kim Đan, tức mới tới được nội dung này. Lore: Kim Đan phải "luyện tâm trong hồng trần" — lệnh tông cho xuống núi lịch
-- luyện ở Hoang Vực sâu, phía đông cổng nội môn.
--
-- =================================================================================================
-- CÂN BẰNG — mọi chỉ số CHỌN TỪ luật biên thật (DuelMargin: biên giấy − 2 nhát phủ đầu − 1 lần né ≥ 2), khoá ở
-- GoldenCoreBalanceTest. Người chơi nền 10/10/10/10, đã qua 3 kiếp (+9 công/thủ/kháng, +90 máu):
--   bộ Nội Môn, Cấp 55 : công 109 · thủ 89  · máu 448
--   bộ Kim Đan, Cấp 60 : công 131 · thủ 107 · máu 508       (bộ Kim Đan: công 92 · thủ 78 · kháng 64 · máu 200)
--
--   wasteland_marauder       Lv55 490/70/54  bộ Nội Môn: biên thật +3 — trận mở, đánh bằng đồ CŨ, trao bộ mới
--   blood_sand_digger        Lv60 540/78/58  bộ Nội Môn: +1 (chặn) · bộ Kim Đan: +4
--   blood_sand_beast_warden  Lv64 570/82/60  bộ Kim Đan: +3
--   frenzied_vein_beast      Lv67 600/84/62  bộ Kim Đan: +2
--   ancient_stone_colossus   Lv70 640/88/64  bộ Nội Môn: −2 (chặn) · bộ Kim Đan: +2
-- Bậc thang: mỗi loài vượt loài trước ở cả máu/công/thủ, loài đầu vượt đàn chủ Huyết Sa (480/68/52).
-- ⚠️ Mọi con số là ĐỀ XUẤT CÂN BẰNG (AI chọn, chủ dự án chưa duyệt từng số).
-- -------------------------------------------------------------------------------------------------

-- 1. Bộ "Kim Đan" (Lv55) — quest mở của arc trao, KHÔNG bán ở Tàng Bảo Các (cùng lý do V45/V48/V109).
INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
SELECT * FROM (VALUES
        ('golden_core_blade',    'item.golden_core_blade.name',    1, 'WEAPON',      60,  0,  0,  0, 55),
        ('golden_core_robe',     'item.golden_core_robe.name',     1, 'ARMOR',        0, 32, 22, 80, 55),
        ('golden_core_cap',      'item.golden_core_cap.name',      1, 'HELMET',       0, 18, 14, 40, 55),
        ('golden_core_gloves',   'item.golden_core_gloves.name',   1, 'GLOVE',       10, 10,  6, 22, 55),
        ('golden_core_boots',    'item.golden_core_boots.name',    1, 'BOOT',         0, 14, 10, 22, 55),
        ('golden_core_talisman', 'item.golden_core_talisman.name', 1, 'ACCESSORY_1', 22,  4, 12, 36, 55)
    ) AS incoming (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = incoming.code);

-- 2. Bảng rơi đồ: đan hồi máu cho cả năm loài; hai trận "trùm" rơi thêm Hộ Linh Phù.
INSERT INTO drop_table (code)
SELECT c.code FROM (VALUES ('wasteland_marauder'), ('blood_sand_digger'), ('blood_sand_beast_warden'),
                           ('frenzied_vein_beast'), ('ancient_stone_colossus')) AS c (code)
 WHERE NOT EXISTS (SELECT 1 FROM drop_table dt WHERE dt.code = c.code);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, v.chance, v.lo, v.hi
  FROM (VALUES ('wasteland_marauder',      'minor_healing_pill',       100, 2, 3),
               ('blood_sand_digger',       'minor_healing_pill',        50, 1, 2),
               ('blood_sand_beast_warden', 'minor_healing_pill',       100, 2, 3),
               ('blood_sand_beast_warden', 'enhance_protection_charm',  35, 1, 1),
               ('frenzied_vein_beast',     'minor_healing_pill',        50, 1, 2),
               ('ancient_stone_colossus',  'minor_healing_pill',       100, 3, 4),
               ('ancient_stone_colossus',  'enhance_protection_charm',  50, 1, 1)) AS v (dt_code, item_code, chance, lo, hi)
  JOIN drop_table dt ON dt.code = v.dt_code
  JOIN item_template it ON it.code = v.item_code
 WHERE NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

-- 3. Năm loài — aggro 3 + retaliates như mọi loài từ V47 (cửa sổ phủ đầu đã tính trong luật biên).
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, aggro_radius_meters, retaliates)
SELECT v.code, 'hud.monster.' || v.code, 'mob_' || replace(v.code, '_', '-'), v.lvl, v.hp, v.atk, 0, v.def, v.res,
       v.hit, v.exp, (SELECT id FROM drop_table WHERE code = v.code), 3, true
  FROM (VALUES ('wasteland_marauder',      55, 490, 70, 54, 44, 96, 2600),
               ('blood_sand_digger',       60, 540, 78, 58, 46, 97, 3000),
               ('blood_sand_beast_warden', 64, 570, 82, 60, 48, 97, 3600),
               ('frenzied_vein_beast',     67, 600, 84, 62, 50, 97, 4000),
               ('ancient_stone_colossus',  70, 640, 88, 64, 52, 98, 5200))
       AS v (code, lvl, hp, atk, def, res, hit, exp)
 WHERE NOT EXISTS (SELECT 1 FROM monster_template mt WHERE mt.code = v.code);

-- 4. Hoang Vực sâu, tiếp về phía đông cổng nội môn: trạm 304 · 312 · 328 · 344 · 360 · trạm 368 · 376 · 392 · 408.
--    Luật giãn: giữa hai điểm 16 > aggro 3+3 và > leash 10 + tầm đánh 2 = 12; 312 − 296 (đàn chủ) = 16 ✓.
--    Trạm [301.5, 306.5] và [365.5, 370.5]: mép cách quái gần nhất đều 5,5 > aggro 3 — cùng khuôn trạm 240 (V109).
--    ⚠️ Mã trạm phải MỚI: `waypost_dead_vein` đã là trạm @127 của V49, và `NOT EXISTS` theo mã sẽ bỏ qua trong im lặng.
--    ⚠️ `DemoPlatforms.GROUND_MAX_X` client VÀ `WorldConstants.MAIN_WORLD_MAX_X` server đi 305 → 417 CÙNG lượt
--    (408 + 9m biên — luật "nền đất phủ hết spawn"; biên server bằng nền đất, ghim ở MainWorldBoundsTest).
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', v.x, 0, 30
  FROM (VALUES ('wasteland_marauder', 312), ('blood_sand_digger', 328), ('blood_sand_digger', 344),
               ('blood_sand_beast_warden', 360), ('frenzied_vein_beast', 376), ('frenzied_vein_beast', 392),
               ('ancient_stone_colossus', 408)) AS v (code, x)
  JOIN monster_template mt ON mt.code = v.code
 WHERE NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.world_code = 'main' AND s.pos_x = v.x);

INSERT INTO safe_zone (code, world_code, min_x, min_y, max_x, max_y, heals)
SELECT v.code, 'main', v.lo, -10, v.hi, 10, false
  FROM (VALUES ('waypost_wasteland_descent', 301.5, 306.5), ('waypost_deep_dead_vein', 365.5, 370.5)) AS v (code, lo, hi)
 WHERE NOT EXISTS (SELECT 1 FROM safe_zone s WHERE s.code = v.code);

INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)
SELECT n.id, 'main', v.x, 0
  FROM npc_template n, (VALUES (304), (368)) AS v (x)
 WHERE n.code = 'waypost_attendant'
   AND NOT EXISTS (SELECT 1 FROM npc_spawn s WHERE s.npc_template_id = n.id AND s.pos_x = v.x);

-- 5. Hoang Vực sâu là đất PvP tự do (STORY §6 arc 4 "Zone: Hoang Vực (PvP mở)", ADR-137): dải từ mép trạm 304 tới hết
--    nền đất. Trạm 368 nằm trong dải nhưng vùng an toàn luôn thắng luật PvP.
INSERT INTO pvp_zone (code, world_code, min_x, max_x)
VALUES ('wasteland_deep', 'main', 307, 417)
ON CONFLICT (code) DO NOTHING;

-- 6. Năm quest chính tuyến arc 4 (nội dung ở script — Điều 27). MỘT câu mỗi quest: câu INSERT…SELECT không thấy dòng
--    chính nó vừa chèn, nên gộp lại thì tiền đề của quest 2–5 thành NULL.
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'descend_the_mountain', 'MAIN', '/scripts/quest/descend_the_mountain.js',
       (SELECT id FROM quest_template WHERE code = 'repel_the_altar_master')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'descend_the_mountain');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'trace_the_blood_sand', 'MAIN', '/scripts/quest/trace_the_blood_sand.js',
       (SELECT id FROM quest_template WHERE code = 'descend_the_mountain')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'trace_the_blood_sand');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'free_the_awakened_beast', 'MAIN', '/scripts/quest/free_the_awakened_beast.js',
       (SELECT id FROM quest_template WHERE code = 'trace_the_blood_sand')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'free_the_awakened_beast');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'quiet_the_frenzied_kin', 'MAIN', '/scripts/quest/quiet_the_frenzied_kin.js',
       (SELECT id FROM quest_template WHERE code = 'free_the_awakened_beast')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'quiet_the_frenzied_kin');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'fell_the_ancient_colossus', 'MAIN', '/scripts/quest/fell_the_ancient_colossus.js',
       (SELECT id FROM quest_template WHERE code = 'quiet_the_frenzied_kin')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'fell_the_ancient_colossus');
