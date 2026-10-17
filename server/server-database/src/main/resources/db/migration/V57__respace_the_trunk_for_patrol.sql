-- V57 — giãn lại CẢ trục chính theo luật chỗ đánh yên tĩnh có vế tuần tra (chủ dự án duyệt
-- 2026-10-17, docs/README.md dòng 64(b) — câu hỏi mới sau V56). CHỈ DỜI DỮ LIỆU — không đổi schema,
-- không đổi chỉ số quái.
--
-- VÌ SAO: V56 mới giãn hai trận tay đôi bắt buộc. Áp luật `|A−B| > tuần tra(A) + leash(B) + tầm
-- đánh` = 3 + 10 + 2 = 15 m cho MỌI cặp quái có aggro thì 16 cặp vi phạm — trục dựng theo khe 13–14 m
-- của luật 12 m cũ (V32), gồm cả trận ĐÓNG arc 2 (cỗ khảo hạch Trúc Cơ @201 cách yêu thú @188 13 m).
-- File này đặt mọi cặp liền kề cách nhau ĐÚNG 16 m, số nhỏ nhất thoả luật.
--
-- NHỮNG ĐIỂM GIỮ NGUYÊN, và vì sao:
--   * thạch vệ @−20: dịch về đông thì chấp sự Hình Vân Sơn @−8 (aggro 0, ĐỨNG YÊN) lọt tầm phát hiện
--     của nó (V41 đã trả giá đúng chỗ này), dịch về tây thì kéo cả cụm phía tây đi theo.
--   * tán tu @21: tây hơn là bệ nhảy [15, 19] (luật spawn-trong-bệ, SpawnGeometryIntegrationTest).
--   * đầu lĩnh @93, tế đồ @120: khe hai bên đã ≥ 24 m.
--   * trạm spirit_vein / east_ridge / dead_vein: không chồng dải tuần tra nào của bố cục mới.
--
-- ⚠️ Nền đất client (`DemoPlatforms`) nới CÙNG đợt: [−55, 210] → [−61, 225], biên 9 m như ba lần
-- nới trước tự đặt. `monster_spawn` sống trong DB còn dải đó là hằng số client — hàng rào duy nhất
-- nối chúng là `SpawnGeometryIntegrationTest.everySpawnFallsWithinTheGroundStrip`.

-- ---------------------------------------------------------------------------------------------
-- 1. Quái — tra theo (mã loài, toạ độ cũ), cùng khuôn V40/V45/V47/V56. Thứ tự không quan trọng:
--    không toạ độ MỚI nào trùng toạ độ CŨ của cùng loài.
-- ---------------------------------------------------------------------------------------------
UPDATE monster_spawn SET pos_x = -52
 WHERE world_code = 'main' AND pos_x = -49
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'trial_construct');

UPDATE monster_spawn SET pos_x = -36
 WHERE world_code = 'main' AND pos_x = -33
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'archive_warden');

UPDATE monster_spawn SET pos_x = 37
 WHERE world_code = 'main' AND pos_x = 34
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'wandering_bandit');

UPDATE monster_spawn SET pos_x = 53
 WHERE world_code = 'main' AND pos_x = 50
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'vein_raider');

UPDATE monster_spawn SET pos_x = 69
 WHERE world_code = 'main' AND pos_x = 66
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'blood_sand_scout');

UPDATE monster_spawn SET pos_x = 136
 WHERE world_code = 'main' AND pos_x = 134
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'blood_sand_acolyte');

UPDATE monster_spawn SET pos_x = 152
 WHERE world_code = 'main' AND pos_x = 148
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'blood_sand_ringleader');

UPDATE monster_spawn SET pos_x = 168
 WHERE world_code = 'main' AND pos_x = 161
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'stone_ogre');

-- Hai yêu thú: dời con XA trước (188 → 200) rồi mới tới con gần (175 → 184) — không phải vì va
-- chạm (không có ràng buộc unique trên toạ độ) mà để mỗi câu chỉ trúng đúng một dòng.
UPDATE monster_spawn SET pos_x = 200
 WHERE world_code = 'main' AND pos_x = 188
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'wasteland_beast');

UPDATE monster_spawn SET pos_x = 184
 WHERE world_code = 'main' AND pos_x = 175
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'wasteland_beast');

UPDATE monster_spawn SET pos_x = 216
 WHERE world_code = 'main' AND pos_x = 201
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'foundation_warden');

-- ---------------------------------------------------------------------------------------------
-- 2. Ba trạm dời theo, mỗi trạm vẫn nằm GIỮA hai dải tuần tra và không chồng dải nào
--    (`SafeZoneIntegrationTest.shouldKeepEveryZoneClearOfEveryPatrolBand`); người trông trạm đứng
--    giữa vùng như V49.
-- ---------------------------------------------------------------------------------------------
UPDATE safe_zone SET min_x = 141.5, max_x = 146.5
 WHERE world_code = 'main' AND code = 'waypost_ringleader_ridge';  -- giữa tế đồ @136 và đàn chủ @152

UPDATE safe_zone SET min_x = 173.5, max_x = 178.5
 WHERE world_code = 'main' AND code = 'waypost_wasteland_gate';    -- giữa quỷ đá @168 và yêu thú @184

UPDATE safe_zone SET min_x = 205.5, max_x = 210.5
 WHERE world_code = 'main' AND code = 'waypost_trial_approach';    -- giữa yêu thú @200 và cỗ khảo hạch @216

UPDATE npc_spawn SET pos_x = 144
 WHERE world_code = 'main' AND pos_x = 141
   AND npc_template_id = (SELECT id FROM npc_template WHERE code = 'waypost_attendant');

UPDATE npc_spawn SET pos_x = 208
 WHERE world_code = 'main' AND pos_x = 195
   AND npc_template_id = (SELECT id FROM npc_template WHERE code = 'waypost_attendant');

UPDATE npc_spawn SET pos_x = 176
 WHERE world_code = 'main' AND pos_x = 168
   AND npc_template_id = (SELECT id FROM npc_template WHERE code = 'waypost_attendant');
