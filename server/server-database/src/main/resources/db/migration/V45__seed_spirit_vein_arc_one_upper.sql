-- V45 — quãng Lv10–18 của arc 1: HAI loài, MỘT bộ trang bị, BA quest, và một cú dời quỷ đá.
--
-- STORY.md §6 xếp arc 1 ở **Lv1–18**, nhưng trọn chuỗi cũ (4 quest) đưa người chơi tới ĐÚNG Cấp 10
-- — con số đó là ràng buộc CỐ Ý của V33 (Cấp 10 = mốc `SelectFactionUseCase.MIN_LEVEL`). Nghĩa là
-- gần nửa sau của arc 1 chưa bao giờ tồn tại. Migration này lấp đúng quãng đó và đóng nó ở Cấp 18.
--
-- Chủ dự án chốt hướng 2026-09-13: **thêm quest arc 1**, "công pháp đầu tiên (Lv15)" hiện thực bằng
-- **một bộ trang bị `required_level 15`** (không dựng cơ chế học chiêu mới — việc đó cần ADR riêng),
-- và **nới nền đất** để có chỗ đặt loài mới.
--
-- =============================================================================================
-- ĐÂY LÀ MIGRATION NỘI DUNG ĐẦU TIÊN SAU TÁM LẦN LIÊN TIẾP CÓ SỬA JAVA — và nó vẫn không sửa
-- =============================================================================================
-- Không cột mới, không bảng mới, không ràng buộc mới, không một dòng Java NỘI DUNG nào. Java có đổi
-- trong đợt này đúng MỘT hằng số hiển thị (`DemoPlatforms.GROUND_MAX_X`, xem §6) — thứ bắt buộc phải
-- đi cùng lượt vì `monster_spawn` sống trong DB còn nền đất là hằng số client, và luật "nền đất phải
-- phủ hết spawn" đã bị vi phạm trong im lặng hai lần trước đây.
--
-- =============================================================================================
-- CHỈ SỐ HAI LOÀI ĐẾN TỪ TEST, KHÔNG TỪ CẢM GIÁC
-- =============================================================================================
-- `SpiritVeinDuelBalanceTest` và `RaiderBandBalanceTest` (server-world) là NƠI ba con số của mỗi
-- loài được CHỌN, đúng khuôn `OldArchiveDuelBalanceTest` đã đặt ra cho V30. Cả hai tính trên lũt đồ
-- SÁU món (ô `ACCESSORY_2` để trống) — đúng lũt đồ mà nhân vật playtest đang mặc và mà mọi lớp cân
-- bằng của dự án vẫn giả định. Máu tối đa mốc arc 1 vì thế là **118**, không phải 122.
--
--   | | máu | atk | def | người chơi cần | nó cần | biên |
--   |---|---|---|---|---|---|---|
--   | `vein_raider` (bộ Tạp Dịch, tới nơi 84 máu)   | 130 | 18 | 12 | 4 nhát | 6 nhát | 2 |
--   | `raider_chieftain` (bộ Ngoại Môn, tới nơi 111) | 200 | 30 | 18 | 4 nhát | 6 nhát | 2 |
--
-- Biên 2 là CỐ Ý: người chơi trúng 91% (`hitPercent = (90 + agi × 0,1)/100`), nên biên 1 là một lần
-- tung đồng xu. Quest 4 giữ biên 3 vì là nhịp đóng arc; hai trận này quay về biên 2 của quest 3.
--
-- ⚠️ Vẫn là số TRÊN GIẤY cho tới khi playtest. Quest 8 đã bị chính playtest bác BA lần trong một
-- ngày. Nếu playtest bác, sửa SỐ chứ đừng sửa kỳ vọng.
-- ---------------------------------------------------------------------------------------------

-- ---------------------------------------------------------------------------------------------
-- 1. Bảng rơi. Hai loài đều là NGƯỜI, nên chúng rơi thứ người mang theo — không có "mảnh đá" nào.
--    Không thêm vật phẩm mới cho việc này: đan dược (V36) và bùa hộ linh (V9) đã có sẵn, và một
--    nguyên liệu thứ hai chỉ để lấp bảng rơi là nội dung chết.
-- ---------------------------------------------------------------------------------------------
INSERT INTO drop_table (code)
SELECT 'vein_raider' WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'vein_raider');

INSERT INTO drop_table (code)
SELECT 'raider_chieftain' WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'raider_chieftain');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 35, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'vein_raider' AND it.code = 'minor_healing_pill'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'raider_chieftain' AND it.code = 'minor_healing_pill'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 20, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'raider_chieftain' AND it.code = 'enhance_protection_charm'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

-- ---------------------------------------------------------------------------------------------
-- 2. Hai loài. `model_key` trỏ tới art ĐÃ DỰNG XONG hôm nay (ASSET-LOG 2026-09-13 và 2026-09-13 (2))
--    — không loài nào mượn model của loài nào, đúng luật
--    `PostgresMonsterCatalogAdapterIntegrationTest.shouldNotLetAnyTwoSpeciesShareAModelKey`.
--
--    `retaliates = true` (ADR-049) cho CẢ HAI: chúng có `aggro_radius_meters` mặc định 6 nên vốn đã
--    tự gây hấn, nhưng cờ này là thứ bảo đảm chúng đánh trả kể cả khi bị đánh từ ngoài tầm aggro.
--    Đây là người cầm vũ khí, không phải cọc tập — ADR-039 D4 chỉ miễn cho mộc nhân.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, retaliates)
SELECT 'vein_raider', 'hud.monster.vein_raider', 'mob_vein-raider', 11, 130, 18, 0, 12, 10,
       92, 140, (SELECT id FROM drop_table WHERE code = 'vein_raider'), true
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'vein_raider');

INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, retaliates)
SELECT 'raider_chieftain', 'hud.monster.raider_chieftain', 'mob_raider-chieftain', 15, 200, 30, 0, 18, 14,
       94, 260, (SELECT id FROM drop_table WHERE code = 'raider_chieftain'), true
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'raider_chieftain');

-- ---------------------------------------------------------------------------------------------
-- 3. DỜI QUỶ ĐÁ 80 → 106 — và đây là quyết định cân bằng, không phải dọn dẹp.
--
--    Đường đông tới sào huyệt (x=93) đi qua tán tu @21, @34, đoạt mạch @47, thám tử @66. Với quỷ đá
--    còn ở @80, nó cộng thêm MỘT nhát nữa — và nó là con đánh đau nhất trục chơi (atk 40). V40 đã
--    dời nó một lần rồi vì ĐÚNG lý do này (47 → 80, sau khi playtest đo được một mình nó lấy 31 máu
--    mỗi lượt đi ngang và biến quest 8 thành thua chắc).
--
--    Sau khi dời, **x ≈ 80 thành khe YÊN TĨNH**: cách thám tử @66 14m và cách sào huyệt @93 13m,
--    tức ngoài tầm aggro (6m) của cả hai. Đó là chỗ người chơi đứng lại hồi máu trước trận cuối
--    (ADR-046: 8 giây ngoài chiến đấu → 3% máu tối đa mỗi giây). Thiết kế KHÔNG bắt buộc họ dừng —
--    `RaiderBandBalanceTest` chứng minh trận đó thắng được kể cả khi đi thẳng — nhưng khe nghỉ có
--    mặt ở đó là chủ đích.
--
--    ⚠️ Quỷ đá vẫn là loài DUY NHẤT chưa quest nào dùng tới (kiểm lại bằng grep toàn bộ script quest
--    + dialogue, 2026-09-13 — V40 nói vậy và sau bốn quest nữa điều đó vẫn đúng), nên dời được mà
--    không phá quest cũ nào.
-- ---------------------------------------------------------------------------------------------
UPDATE monster_spawn
   SET pos_x = 106
 WHERE world_code = 'main'
   AND pos_x = 80
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'stone_ogre');

-- ---------------------------------------------------------------------------------------------
-- 4. Hai điểm spawn. BỐN ràng buộc hình học đều kiểm (khuôn V40 §4):
--
--    `vein_raider` @ **47** — chỗ quỷ đá dọn đi ở V40, khe hợp lệ duy nhất còn lại của trục cũ:
--      (a) trong nền đất; (b) cách tán tu @34 **13m** > aggro 6+6 ✓ (V29);
--      (c) 13m > leash 10 + tầm đánh 2 ✓ (V32); (d) ngoài vùng an toàn [-15,5], ngoài mọi bệ nhảy ✓.
--      Phía đông: cách thám tử @66 **19m** ✓.
--
--    `raider_chieftain` @ **93** — đất mới:
--      (a) trong nền đất MỚI [-55, 115]; (b) cách thám tử @66 **27m** ✓; (c) cách quỷ đá @106 (vừa
--      dời) **13m** ✓ cả hai luật; (d) ngoài vùng an toàn, ngoài mọi bệ nhảy ✓.
--
--    `respawn_seconds`: đoạt mạch **20** (quest 10 đòi ba con mà chỉ có MỘT điểm spawn — 20 giây
--    vừa là nhịp chờ vừa là thừa thời gian để `HealthRegenSystem` kéo máu về đầy); đầu lĩnh **300**
--    (hắn là mốc đóng arc, không phải chỗ cày).
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 47, 0, 20
  FROM monster_template mt
 WHERE mt.code = 'vein_raider'
   AND NOT EXISTS (
       SELECT 1 FROM monster_spawn ms
        WHERE ms.monster_template_id = mt.id AND ms.world_code = 'main');

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 93, 0, 300
  FROM monster_template mt
 WHERE mt.code = 'raider_chieftain'
   AND NOT EXISTS (
       SELECT 1 FROM monster_spawn ms
        WHERE ms.monster_template_id = mt.id AND ms.world_code = 'main');

-- ---------------------------------------------------------------------------------------------
-- 5. Bộ "NGOẠI MÔN" — `required_level 15`, SÁU món.
--
--    Đây là "công pháp đầu tiên (Lv15)" của STORY.md §6, hiện thực bằng cơ chế ĐÃ CHẠY (ADR-036 —
--    trang bị có chỉ số thật) thay vì một cơ chế học chiêu mới (việc đó chạm SkillRegistry và có thể
--    chạm protocol ⇒ cần ADR riêng, CLAUDE.md §4). Chủ dự án chọn hướng này 2026-09-13.
--
--    Nó lấp luôn một lỗ khác đo được cùng ngày: trước migration này trang bị DỪNG ở
--    `required_level 5` (bộ "Hạ Nhẫn"), nên cả quãng Lv10–18 không có gì để cày tới.
--
--    **SÁU món, cố ý không phải bảy.** Ô `ACCESSORY_2` để trống, đúng bằng lũt đồ mà mọi lớp cân
--    bằng của dự án giả định và đúng bằng thứ nhân vật playtest đang mặc. Bộ được TRAO khớp chính
--    xác bộ được TÍNH — không để khe hở nào giữa hai thứ đó.
--
--    Tổng cộng: **atk 34 · def 30 · res 24 · hp 70** ⇒ người chơi Cấp 15 thành ATK 64 · DEF 50 ·
--    HP 170. Trên bộ "Hạ Nhẫn" sáu món (atk 26 · def 23 · hp 48) ở cả ba trục — một bộ mở ở Cấp 15
--    mà thua bộ mở ở Cấp 5 thì không ai mặc (`RaiderBandBalanceTest` gác điều này).
--
--    ⚠️ **KHÔNG bán ở Tàng Bảo Các.** Bộ này là phần thưởng quest 11, và `RaiderBandBalanceTest`
--    chứng minh nó là ĐIỀU KIỆN CẦN của quest 12 — bán nó đi là cho phép người chơi mua đường tắt
--    qua chính nhịp mà chuỗi quest dựng ra. Bộ "Hạ Nhẫn" 10.900 vàng vẫn là đường mua cho ai muốn
--    mạnh sớm.
-- ---------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
SELECT * FROM (VALUES
        ('outer_sect_blade',    'item.outer_sect_blade.name',    1, 'WEAPON',      22,  0,  0,  0, 15),
        ('outer_sect_robe',     'item.outer_sect_robe.name',     1, 'ARMOR',        0, 12,  8, 26, 15),
        ('outer_sect_cap',      'item.outer_sect_cap.name',      1, 'HELMET',       0,  7,  5, 14, 15),
        ('outer_sect_gloves',   'item.outer_sect_gloves.name',   1, 'GLOVE',        4,  4,  2,  8, 15),
        ('outer_sect_boots',    'item.outer_sect_boots.name',    1, 'BOOT',         0,  5,  4,  8, 15),
        ('outer_sect_talisman', 'item.outer_sect_talisman.name', 1, 'ACCESSORY_1',  8,  2,  5, 14, 15)
    ) AS incoming (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = incoming.code);

-- ---------------------------------------------------------------------------------------------
-- 6. Ba quest. Nội dung (số lần hạ, thứ tự bước, phần thưởng) nằm HẾT trong script (Điều 27); bảng
--    này chỉ giữ định danh + đường dẫn script + điều kiện MỞ.
--
--    Chuỗi arc 1 đầy đủ sau migration này:
--      kill_training_dummies → night_watch_bandit → clear_old_archive → awakening_trial
--        → **spirit_vein_raiders → first_cultivation_manual → break_the_raider_band**
--
--    ⚠️ `report_to_outer_sect` (arc 2 nhịp MỞ) được TRỎ LẠI ở §7 — arc 2 không được mở trước khi
--    arc 1 đóng thật sự, và tới hôm nay "đóng" mới có nghĩa là Cấp 18 như STORY khai.
-- ---------------------------------------------------------------------------------------------
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'spirit_vein_raiders', 'MAIN', '/scripts/quest/spirit_vein_raiders.js',
       (SELECT id FROM quest_template WHERE code = 'awakening_trial')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'spirit_vein_raiders');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'first_cultivation_manual', 'MAIN', '/scripts/quest/first_cultivation_manual.js',
       (SELECT id FROM quest_template WHERE code = 'spirit_vein_raiders')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'first_cultivation_manual');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'break_the_raider_band', 'MAIN', '/scripts/quest/break_the_raider_band.js',
       (SELECT id FROM quest_template WHERE code = 'first_cultivation_manual')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'break_the_raider_band');

-- ---------------------------------------------------------------------------------------------
-- 7. TRỎ LẠI tiên quyết của `report_to_outer_sect`: `awakening_trial` → `break_the_raider_band`.
--
--    V37 trỏ nó về `awakening_trial` vì lúc đó đó LÀ nhịp đóng arc 1. Nay arc 1 dài thêm ba quest,
--    nên mốc đóng đã dời. Không sửa V37 (đã push — luật migration: đã push thì ra version mới), mà
--    UPDATE ở đây.
--
--    Hệ quả có chủ đích: người chơi phải đi hết Lv10→18 trước khi Chu Hoành bàn giao họ cho ngoại
--    môn. Đó chính là thứ làm dải cấp của STORY §6 thành sự thật lần đầu tiên.
-- ---------------------------------------------------------------------------------------------
UPDATE quest_template
   SET prerequisite_quest_id = (SELECT id FROM quest_template WHERE code = 'break_the_raider_band')
 WHERE code = 'report_to_outer_sect';
