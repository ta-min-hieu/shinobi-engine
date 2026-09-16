-- V47 — quãng Lv22–27 của arc 2: HAI loài Huyết Sa, BA điểm spawn, BA quest.
--
-- =============================================================================================
-- VÌ SAO CÓ ĐỢT NÀY
-- =============================================================================================
-- STORY.md §6 xếp arc 2 (Ngoại Môn, Trúc Cơ) ở **Lv19–36**. Sau V45/V46 và playtest trọn chuỗi
-- 2026-09-13, mười hai quest đưa người chơi tới **Cấp 22** — tức mới chạm ĐẦU dải. Quãng Lv22–36
-- không có một quest nào, đúng hình dạng của lỗ Lv10–18 mà V45 đã lấp cho arc 1.
--
-- Chủ dự án chốt 2026-09-13: làm **một lát cắt Lv22–27 trước**, đo thật rồi mới viết tiếp — thay vì
-- viết 11–14 quest trên giấy cho trọn Lv22–36 (39.900 EXP).
--
-- =============================================================================================
-- NHỊP TƯỜNG THUẬT: NỐI ĐÚNG CHỖ ARC 2 ĐỂ HỞ
-- =============================================================================================
-- Quest 9 (`confront_the_steward`) đóng cao trào arc 2 bằng một cái kết DANG DỞ, đúng §6: "đánh
-- bại, nhưng hắn kịp tự tan tu vi, manh mối đứt". Ba quest này nhặt đúng sợi đó lên: hộ pháp chết
-- rồi, nhưng thứ hắn đào dở ở vùng mạch chết phía đông thì vẫn nằm đấy — và giáo phái cử người
-- thường tới lấy.
--
-- ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2) — ba quest này KHÔNG được chạm:
--   · vết nứt Thiên Môn / chân tướng Đại Kiếp (arc 3 mới gieo nghi, arc 6 mới lộ);
--   · Sa Y Nhân và mục đích thật của giáo (§4.3 nói rõ, nhưng đó là arc 6);
--   · và tuyệt đối không một chữ nào về Tô Vân Nhi (twist arc 5).
-- Ở đây chúng chỉ là tà tu đi nhặt đồ và bắt người lấy tu vi — đúng phần §4.3 cho phép nói.
--
-- =============================================================================================
-- CHỈ SỐ HAI LOÀI ĐẾN TỪ RÀNG BUỘC, KHÔNG TỪ CẢM GIÁC
-- =============================================================================================
-- Người chơi Cấp 22 mặc trọn bộ "Ngoại Môn" (đo thật 2026-09-13): **atk 64 · def 50 · máu 170**.
--
-- ⚠️ **MÁU LÚC TỚI NƠI LÀ 143, KHÔNG PHẢI 170** — và đây là chỗ bản nháp đầu của chính migration
-- này SAI. Đi THẲNG từ tông ra x=120 thì ăn 2 nhát tán tu (15 ×2), 1 nhát thám tử (17) và 1 nhát
-- quỷ đá (27) ⇒ tới nơi còn **96**, và ở mức đó KHÔNG tồn tại bộ chỉ số nào vừa mạnh hơn đầu lĩnh
-- arc 1 vừa giữ biên ≥2 (quét hp 210–400 × atk 32–44 × def 20–34: rỗng). Tức lát cắt thua chắc.
--
-- Chủ dự án chốt 2026-09-14: **được phép tính CÓ hồi máu ở khe yên tĩnh @80** — khe đó do chính
-- V45 tạo ra cho đúng việc này (ADR-046: 8 giây ngoài chiến đấu → 3% máu tối đa mỗi giây), và
-- quãng 120m mà vẫn trừ tuyến tính từ cổng tông là phi thực tế.
--
-- ⚠️ **BẢN ĐẦU CỦA ĐOẠN NÀY SAI, VÀ PLAYTEST ĐÃ BÁC NÓ.** Nó viết "từ @80 ra @120 chỉ còn quỷ đá
-- @106 trên đường ⇒ arrivalHp = 170 − 27 = 143", dựa trên giả định rằng V46 hạ đoạt mạch xuống
-- aggro 3 thì chúng "không với tới người đi ngang". `gameProbe` 2026-09-14 đo ngược lại: aggro 3
-- VẪN đánh trúng đúng MỘT nhát (đoạt mạch @47 đánh 12, đầu lĩnh @93 đánh 20 — hai lần xác nhận).
-- V46 thu hẹp cửa sổ phủ đầu chứ không xoá nó, đúng như chính V46 đã ghi sau khi được sửa lại.
-- Đo thật: nghỉ đủ ở @80 rồi đi tiếp thì tới mạch chết còn **123** máu, và ở mức đó biên chỉ là
-- **+1** — thắng còn 8 máu, và chỉ nhờ một đòn chí mạng.
--
-- Chủ dự án chốt (lượt hai, cùng ngày): **dời quỷ đá 106 → 161**, ra SAU đàn chủ @148 — xem mục 5
-- cuối file. Từ @80 ra @120 khi đó chỉ còn ĐẦU LĨNH @93 với tới ⇒ **arrivalHp = 170 − 20 = 150**,
-- và ở 150 thì tế đồ cần 7 nhát còn người chơi cần 5 ⇒ **biên +2**, đúng mức của một nhịp MỞ.
--
-- ⚠️ Đây là ĐỔI LUẬT NỀN so với `SpiritVeinDuelBalanceTest`, lớp đó khai ngược lại: *"thiết kế
-- phải đứng vững cả khi họ KHÔNG dừng"*. Luật cũ vẫn đúng cho quãng 47m của arc 1; luật mới chỉ
-- áp cho quãng ≥100m, nơi có khe nghỉ hợp lệ nằm giữa đường. Ghi rõ ở đây để lần sau không ai
-- lặng lẽ trộn hai luật.
--
-- Với công thức hit = round(atk × 100/(100+def)):
--
--   | loài                  | máu | atk | def | ta đánh | ta cần | nó đánh | nó cần | BIÊN |
--   |---|---|---|---|---|---|---|---|---|
--   | blood_sand_acolyte    | 220 | 34  | 26  | 51      | 5 nhát | 23      | 7 nhát | **2** |
--   | blood_sand_ringleader | 240 | 35  | 28  | 50      | 5 nhát | 23      | 7 nhát | **2** |
--
-- Biên 2 là mức V45 chọn cho quái thường; AwakeningTrialBalanceTest chọn 3 cho nhịp đóng arc.
-- ⚠️ **Biên 3 vẫn BẤT KHẢ** kể cả với arrivalHp 143: nó đòi acolyte xuống hp 200, tức BẰNG đầu lĩnh
-- arc 1 chứ không hơn — phá luật bậc thang. Đó là hệ quả đo được của việc bộ Ngoại Môn mạnh hơn
-- thang quái hiện có (người chơi đánh đầu lĩnh arc 1 với biên 5), và nó sẽ còn siết chặt hơn ở
-- Lv28–36. Lúc đó là vấn đề THIẾT KẾ, không phải con số.
--
-- Đàn chủ lớn hơn tế đồ ở **CẢ BA** trục (240>220, 35>34, 28>26) — cùng ràng buộc mà
-- theChieftainShouldBeAStepAboveHisOwnRaiders đặt ra cho cặp đoạt mạch. ⚠️ atk là **35**, không
-- phải 36: với 36 thì hắn cần hp ≤ 200 để giữ biên 2, mà hp phải > 220 để trên tế đồ — mâu thuẫn.
-- Một điểm atk là đủ để giữ bậc thang mà không phá biên.
--
-- =============================================================================================
-- AGGRO 3 NGAY TỪ ĐẦU — ÁP THẲNG BÀI HỌC V46
-- =============================================================================================
-- V46 phải hạ vein_raider/raider_chieftain từ 6 xuống 3 sau khi playtest đo được: aggro 6 gấp ba
-- ATTACK_RANGE_METERS (2) nên quái chạy tự do 4m và đánh MIỄN PHÍ trong lúc người chơi còn đang đi
-- tới. Hai loài mới sinh ra với aggro 3 để không phải sửa lần nữa.
--
-- retaliates = true (ADR-049): chúng không đánh trước từ xa, nhưng đánh vào là đánh trả ngay.
-- ---------------------------------------------------------------------------------------------

-- 1. Bảng rơi. Hai loài đều là NGƯỜI nên rơi thứ người mang theo; không thêm vật phẩm mới nào —
--    đan dược (V36) và bùa hộ linh (V9) đã có sẵn, và một nguyên liệu chỉ để lấp bảng rơi là nội
--    dung chết (cùng lập luận V45).
INSERT INTO drop_table (code)
SELECT 'blood_sand_acolyte' WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'blood_sand_acolyte');

INSERT INTO drop_table (code)
SELECT 'blood_sand_ringleader' WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'blood_sand_ringleader');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 40, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'blood_sand_acolyte' AND it.code = 'minor_healing_pill'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'blood_sand_ringleader' AND it.code = 'minor_healing_pill'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 25, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'blood_sand_ringleader' AND it.code = 'enhance_protection_charm'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

-- 2. Hai loài. model_key trỏ tới art ĐÃ DỰNG XONG (ASSET-LOG 2026-09-14) — không loài nào mượn
--    model của loài nào, đúng luật V45 đặt ra.
--    exp_reward cao hơn blood_sand_enforcer (300) vì chúng đứng xa hơn và dày hơn.
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, aggro_radius_meters, retaliates)
SELECT 'blood_sand_acolyte', 'hud.monster.blood_sand_acolyte', 'mob_blood-sand-acolyte', 22, 220, 34, 0, 26, 20,
       93, 320, (SELECT id FROM drop_table WHERE code = 'blood_sand_acolyte'), 3, true
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'blood_sand_acolyte');

INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, aggro_radius_meters, retaliates)
SELECT 'blood_sand_ringleader', 'hud.monster.blood_sand_ringleader', 'mob_blood-sand-ringleader', 25, 240, 35, 0, 28, 22,
       95, 420, (SELECT id FROM drop_table WHERE code = 'blood_sand_ringleader'), 3, true
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'blood_sand_ringleader');

-- 3. Ba điểm spawn ở vùng mạch chết phía đông: 120, 134, 148.
--
--    Luật hình học (V29 |A−B| > aggroA + aggroB, V32 > leash + tầm đánh, và "không nằm trong bệ
--    nhảy"): khe 14m giữa mỗi cặp, trong khi cặp chặt nhất đòi >12 (leash 10 + tầm đánh 2). Sau
--    khi dời quỷ đá sang @161, hai đầu vùng là đầu lĩnh @93 (cách 27m) và chính quỷ đá @161 (cách
--    13m > aggro 6+3 = 9 và > 12). Ba bệ nhảy nằm ở [3,7] [9,13] [15,19] và vùng an
--    toàn ở [-15, 5] — không điểm nào chạm tới.
--
--    ⚠️ DemoPlatforms.GROUND_MAX_X đi **115 → 170** cùng đợt (lần thứ BA). Nền đất là hằng số
--    client còn spawn sống trong DB, nên luật "nền đất phải phủ hết spawn" bắt hai thứ đi cùng lượt.
--    170 = 161 + 9m biên (điểm xa nhất nay là quỷ đá sau khi dời, không phải đàn chủ @148), đúng
--    khoảng dư mà V45 để lại sau quỷ đá @106.
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 120, 0, 30 FROM monster_template mt
 WHERE mt.code = 'blood_sand_acolyte'
   AND NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.monster_template_id = mt.id AND s.pos_x = 120);

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 134, 0, 30 FROM monster_template mt
 WHERE mt.code = 'blood_sand_acolyte'
   AND NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.monster_template_id = mt.id AND s.pos_x = 134);

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 148, 0, 30 FROM monster_template mt
 WHERE mt.code = 'blood_sand_ringleader'
   AND NOT EXISTS (SELECT 1 FROM monster_spawn s WHERE s.monster_template_id = mt.id AND s.pos_x = 148);

-- 4. Ba quest. Nội dung (số lần hạ, thứ tự bước, phần thưởng) nằm HẾT trong script (Điều 27); bảng
--    này chỉ giữ định danh + đường dẫn script + điều kiện MỞ.
--
--    Chuỗi arc 2 sau migration này:
--      report_to_outer_sect → recover_outer_sect_tallies → missing_disciple_trail
--        → abandoned_cave → confront_the_steward
--        → **dead_vein_intruders → blood_sand_trail → scatter_the_blood_sand_band**
--
--    EXP 3.600 / 4.000 / 4.400 = 12.000, đúng tổng cần để đi Cấp 22 → 27 (ExperienceCurve tuyến
--    tính 100 × cấp). Nhịp cũ tăng dần 500 → 3.700; 4.000 là bước kế tiếp chứ không phải nhảy bậc,
--    và nó mua bằng quãng đường: x=120–148 xa gấp ~1,6 lần sào huyệt @93 của quest 12.
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'dead_vein_intruders', 'MAIN', '/scripts/quest/dead_vein_intruders.js',
       (SELECT id FROM quest_template WHERE code = 'confront_the_steward')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'dead_vein_intruders');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'blood_sand_trail', 'MAIN', '/scripts/quest/blood_sand_trail.js',
       (SELECT id FROM quest_template WHERE code = 'dead_vein_intruders')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'blood_sand_trail');

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'scatter_the_blood_sand_band', 'MAIN', '/scripts/quest/scatter_the_blood_sand_band.js',
       (SELECT id FROM quest_template WHERE code = 'blood_sand_trail')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'scatter_the_blood_sand_band');


-- =================================================================================================
-- 5. DỜI QUỶ ĐÁ 106 → 161 — lần thứ BA, và lần này số đo quyết định chứ không phải suy luận.
-- =================================================================================================
-- V40 dời nó 47 → 80 (ra sau hang bỏ hoang), V45 dời 80 → 106 (ra sau sào huyệt @93). Mỗi lần cùng
-- một lý do: `stone_ogre` là con đánh đau nhất trục chơi (atk 40 ⇒ 27 máu lên giáp 50 của bộ Ngoại
-- Môn) và mỗi nội dung mới lại mọc ra PHÍA SAU nó.
--
-- Lần này có số đo thật (`gameProbe`, 2026-09-14): nghỉ đủ ở khe @80 rồi đi ra mạch chết @120 thì
-- tới nơi còn **123** máu — đầu lĩnh @93 lấy 20 (aggro 3 VẪN với tới, một nhát) và quỷ đá @106 lấy
-- 27. Ở 123 máu, trận tế đồ chỉ còn biên **+1**: đo được thắng còn 8 máu và chỉ nhờ một chí mạng.
-- Bỏ nhát 27 của quỷ đá ra thì tới nơi **150** ⇒ tế đồ cần 7 nhát, người chơi cần 5 ⇒ **biên +2**.
--
-- Vì sao 161: luật giãn đòi khe > leash 10 + tầm đánh 2 = 12 tính từ đàn chủ @148, và > aggro
-- (6 + 3) = 9. 161 cho khe 13m, thoả cả hai với dư 1m. Nền đất đi 157 → 170 cùng lượt (161 + 9m
-- biên, đúng quy ước V40/V45 tự đặt).
--
-- ⚠️ Người chơi muốn săn quỷ đá VẪN tới được — nó chỉ lùi ra sau đàn chủ, không biến mất. Cùng câu
-- V40 đã ghi khi dời nó lần đầu.
--
-- KHÔNG đụng aggro/atk của nó: phương án "cho aggro = 0 + retaliates" cũng đạt biên +2 nhưng sẽ
-- biến con đánh đau nhất trục chơi thành một con không bao giờ gây sự — đổi tính cách một loài đã
-- chạy từ V23, trong khi dời chỗ là nước đi dự án đã dùng hai lần cho đúng tình huống này.
UPDATE monster_spawn
   SET pos_x = 161
 WHERE world_code = 'main'
   AND pos_x = 106
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'stone_ogre');
