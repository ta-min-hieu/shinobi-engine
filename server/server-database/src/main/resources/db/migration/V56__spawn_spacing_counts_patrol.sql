-- V56 — luật giãn spawn tính cả vế TUẦN TRA (chủ dự án duyệt 2026-10-17, docs/README.md dòng 64(b)).
-- CHỈ DỜI HAI điểm spawn — không đổi schema, không đổi chỉ số quái.
--
-- VÌ SAO: luật V32 là `|A−B| > leash(B) + tầm đánh` = 12 m, coi con đang bị đánh như đứng yên ở điểm
-- spawn. Nó không đứng yên: nó đi tuần tới `patrol_radius_meters` (3 m, mặc định V25) và người chơi
-- đánh theo. Kiểm sống toàn chuỗi 2026-10-04 đo được đúng chỗ đó:
--   * cỗ khảo hạch @−47, thạch vệ @−33 (cách 14 m): đánh ở −41…−43 thì thạch vệ đuổi tới ⇒ 2/2 chết.
--   * đoạt mạch @47, tán tu @34 (cách 13 m): đứng 45 chết, đứng 50 thắng.
-- Luật mới `|A−B| > patrol(A) + leash(B) + tầm đánh` = 3 + 10 + 2 = 15 m
-- (`PostgresMonsterCatalogAdapterIntegrationTest.shouldLeaveEveryAggroSpawnAQuietPlaceToFightIn`).
--
-- HAI NƯỚC DỜI, mỗi nước là số nhỏ nhất thoả luật mà không phá luật nào khác:
--   * cỗ khảo hạch −47 → −49: cách thạch vệ @−33 16 m. Vẫn trong dải nền đất client
--     (`DemoPlatforms.GROUND_MIN_X` = −55, còn 6 m).
--   * đoạt mạch 47 → 50: cách tán tu @34 16 m, cách thám tử Huyết Sa @66 16 m — đúng giữa khe.
UPDATE monster_spawn
   SET pos_x = -49
 WHERE world_code = 'main'
   AND pos_x = -47
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'trial_construct');

UPDATE monster_spawn
   SET pos_x = 50
 WHERE world_code = 'main'
   AND pos_x = 47
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'vein_raider');
