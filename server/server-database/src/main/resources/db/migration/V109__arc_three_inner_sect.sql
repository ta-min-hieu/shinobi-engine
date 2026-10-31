-- V109 — ARC 3 "Nội Môn" (Tử Phủ, Lv37–54), STORY.md §6 — ADR-130, lô 24.
--
-- Nối sau `foundation_breakthrough` (V48). Cấp 36 là trần Thiên Kiếp (ADR-126): người chơi phải độ kiếp mới bước vào
-- Tử Phủ, tức mới tới được nội dung này — khớp lore "Trúc Cơ thành công + qua khảo hạch (arc 3, ~Lv37)" (STORY §4.1).
--
-- =================================================================================================
-- CÂN BẰNG — mọi chỉ số CHỌN TỪ luật biên thật (DuelMargin: biên giấy − 2 nhát phủ đầu − 1 lần né ≥ 2), khoá ở
-- InnerSectBalanceTest. Người chơi nền 10/10/10/10, đã qua 2 kiếp (+6 công/thủ/kháng, +60 máu):
--   bộ Trúc Cơ, Cấp 37 : công 88  · thủ 71 · máu 338
--   bộ Nội Môn, Cấp 42 : công 106 · thủ 86 · máu 392        (bộ Nội Môn: công 70 · thủ 60 · kháng 48 · máu 150)
--
--   inner_trial_construct   Lv37 360/52/40  bộ Trúc Cơ: biên thật +3  — trận mở, đánh bằng đồ CŨ, trao bộ mới
--   blood_sand_infiltrator  Lv42 400/60/46  bộ Trúc Cơ: 0 (chặn) · bộ Nội Môn: +4
--   bach_kinh_chi           Lv46 450/66/50  bộ Nội Môn: +2
--   blood_sand_altar_master Lv52 480/68/52  bộ Trúc Cơ: −2 (chặn) · bộ Nội Môn: +2
-- Bậc thang: mỗi loài vượt loài trước ở cả máu/công/thủ, và loài đầu vượt foundation_warden (300/37/35).
-- ⚠️ Mọi con số là ĐỀ XUẤT CÂN BẰNG (AI chọn, chủ dự án chưa duyệt từng số).
-- -------------------------------------------------------------------------------------------------

-- 1. Bộ "Nội Môn" (Lv37) — quest mở của arc trao, KHÔNG bán ở Tàng Bảo Các (cùng lý do V45/V48).
INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
SELECT * FROM (VALUES
        ('inner_sect_blade',    'item.inner_sect_blade.name',    1, 'WEAPON',      46,  0,  0,  0, 37),
        ('inner_sect_robe',     'item.inner_sect_robe.name',     1, 'ARMOR',        0, 24, 16, 60, 37),
        ('inner_sect_cap',      'item.inner_sect_cap.name',      1, 'HELMET',       0, 14, 10, 30, 37),
        ('inner_sect_gloves',   'item.inner_sect_gloves.name',   1, 'GLOVE',        8,  8,  4, 16, 37),
        ('inner_sect_boots',    'item.inner_sect_boots.name',    1, 'BOOT',         0, 10,  8, 16, 37),
        ('inner_sect_talisman', 'item.inner_sect_talisman.name', 1, 'ACCESSORY_1', 16,  4, 10, 28, 37)
    ) AS incoming (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = incoming.code);

-- 2. Bảng rơi đồ: đan hồi máu cho cả bốn loài; hai trận "trùm" rơi thêm Hộ Linh Phù.
INSERT INTO drop_table (code)
SELECT c.code FROM (VALUES ('inner_trial_construct'), ('blood_sand_infiltrator'), ('bach_kinh_chi'),
                           ('blood_sand_altar_master')) AS c (code)
 WHERE NOT EXISTS (SELECT 1 FROM drop_table dt WHERE dt.code = c.code);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, v.chance, v.lo, v.hi
  FROM (VALUES ('inner_trial_construct',   'minor_healing_pill',       100, 2, 3),
               ('blood_sand_infiltrator',  'minor_healing_pill',        50, 1, 2),
               ('bach_kinh_chi',           'minor_healing_pill',       100, 2, 3),
               ('bach_kinh_chi',           'enhance_protection_charm',  35, 1, 1),
               ('blood_sand_altar_master', 'minor_healing_pill',       100, 3, 4),
               ('blood_sand_altar_master', 'enhance_protection_charm',  50, 1, 1)) AS v (dt_code, item_code, chance, lo, hi)
  JOIN drop_table dt ON dt.code = v.dt_code
  JOIN item_template it ON it.code = v.item_code
 WHERE NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

-- 3. Bốn loài — aggro 3 + retaliates như mọi loài từ V47 (cửa sổ phủ đầu đã tính trong luật biên).
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, aggro_radius_meters, retaliates)
SELECT v.code, 'hud.monster.' || v.code, 'mob_' || replace(v.code, '_', '-'), v.lvl, v.hp, v.atk, 0, v.def, v.res,
       v.hit, v.exp, (SELECT id FROM drop_table WHERE code = v.code), 3, true
  FROM (VALUES ('inner_trial_construct',   37, 360, 52, 40, 30, 96,  900),
               ('blood_sand_infiltrator',  42, 400, 60, 46, 36, 96, 1100),
               ('bach_kinh_chi',           46, 450, 66, 50, 40, 97, 1500),
               ('blood_sand_altar_master', 52, 480, 68, 52, 42, 98, 2200))
       AS v (code, lvl, hp, atk, def, res, hit, exp)
 WHERE NOT EXISTS (SELECT 1 FROM monster_template mt WHERE mt.code = v.code);

-- 4. Đường chính đi tiếp về phía đông: 232 · trạm 240 · 248 · 264 · 280 · 296 (bước 16 như V57).
--    Luật giãn: giữa hai điểm 16 > aggro 3+3 và > leash 10 + tầm đánh 2 = 12; 232 − 216 (foundation_warden) = 16 ✓.
--    Trạm tiếp ứng [237.5, 242.5]: mép cách 232 và 248 đều 5,5 > aggro 3, cùng khuôn trạm 208 ↔ quái 216.
--    ⚠️ `DemoPlatforms.GROUND_MAX_X` client đi 225 → 305 CÙNG lượt (296 + 9m biên — luật "nền đất phủ hết spawn").
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', v.x, 0, 30
  FROM (VALUES ('inner_trial_construct', 232), ('blood_sand_infiltrator', 248), ('blood_sand_infiltrator', 264),
               ('bach_kinh_chi', 280), ('blood_sand_altar_master', 296)) AS v (code, x)
  JOIN monster_template mt ON mt.code = v.code
 WHERE NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.world_code = 'main' AND s.pos_x = v.x);

INSERT INTO safe_zone (code, world_code, min_x, min_y, max_x, max_y, heals)
SELECT 'waypost_inner_sect_gate', 'main', 237.5, -10, 242.5, 10, false
 WHERE NOT EXISTS (SELECT 1 FROM safe_zone WHERE code = 'waypost_inner_sect_gate');

INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)
SELECT n.id, 'main', 240, 0 FROM npc_template n
 WHERE n.code = 'waypost_attendant'
   AND NOT EXISTS (SELECT 1 FROM npc_spawn s WHERE s.npc_template_id = n.id AND s.pos_x = 240);

-- 5. Năm quest chính tuyến arc 3 (nội dung ở script — Điều 27). MỘT câu mỗi quest: câu INSERT…SELECT không
--    thấy dòng chính nó vừa chèn, nên gộp lại thì tiền đề của quest 2–5 thành NULL.
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'inner_sect_examination', 'MAIN', '/scripts/quest/inner_sect_examination.js',
       (SELECT id FROM quest_template WHERE code = 'foundation_breakthrough')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'inner_sect_examination');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'forbidden_archive_glow', 'MAIN', '/scripts/quest/forbidden_archive_glow.js',
       (SELECT id FROM quest_template WHERE code = 'inner_sect_examination')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'forbidden_archive_glow');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'leaked_examination', 'MAIN', '/scripts/quest/leaked_examination.js',
       (SELECT id FROM quest_template WHERE code = 'forbidden_archive_glow')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'leaked_examination');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'inner_sect_grand_contest', 'MAIN', '/scripts/quest/inner_sect_grand_contest.js',
       (SELECT id FROM quest_template WHERE code = 'leaked_examination')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'inner_sect_grand_contest');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'repel_the_altar_master', 'MAIN', '/scripts/quest/repel_the_altar_master.js',
       (SELECT id FROM quest_template WHERE code = 'inner_sect_grand_contest')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'repel_the_altar_master');
