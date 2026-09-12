-- V40 — quest THỨ TÁM: "Hang Bỏ Hoang" (STORY.md §6 arc 2, nhịp TWIST — cái tên Huyết Sa lần đầu).
--
-- Migration này thêm NHIỀU thứ hơn năm quest trước cộng lại (một loài, một vật phẩm, một bảng rơi,
-- một điểm spawn, một quest) vì nó là nhịp mở ra một ĐỊA ĐIỂM mới. Vẫn không cột mới, không bảng
-- mới, không dòng Java nội dung nào.
--
-- =============================================================================================
-- ⚠️ BẢN ĐỒ ĐÃ HẾT CHỖ — VÀ ĐÓ LÀ LÝ DO NỀN ĐẤT PHẢI NỚI CÙNG MIGRATION NÀY
-- =============================================================================================
-- Đo bằng máy trước khi chọn vị trí: với một loài `aggro_radius_meters = 6`, luật giãn V29
-- (`|A−B| > aggroA + aggroB`), vùng an toàn V35 `[-15, 5]` và ba bệ nhảy `[3,7] · [9,13] · [15,19]`,
-- cả đoạn nền đất CŨ `[-55, 60]` chỉ còn ĐÚNG HAI khe hợp lệ:
--
--   · một khe rộng 1 mét ngay chỗ hình nộm đang đứng (x ≈ 8) — vô lý về hình ảnh;
--   · đúng một điểm x = 59.5, sát mép nền đất, không còn biên an toàn nào.
--
-- Tức là sáu quest vừa qua đã lấp kín trục chơi. Chủ dự án chọn **nới nền đất về đông** (thay vì
-- dựng hầm ngục thứ hai — hiện chỉ có MỘT hầm ngục hardcode trong Java, và việc đó còn chạm vùng
-- ADR-047 đang chờ quyết). `DemoPlatforms.GROUND_MAX_X` vì thế đi từ **60 → 85** trong CÙNG đợt
-- này: `monster_spawn` sống trong DB còn nền đất là hằng số client, không có gì nối hai thứ đó lại
-- ngoài dòng ghi chú ở chính hằng số ấy — và luật "nền đất phải phủ hết spawn" đã bị vi phạm trong
-- im lặng hai lần trước đây (bản đầu -30 khi thạch vệ đã ở -33; rồi sân khảo hạch -47).
--
-- Vị trí hang: **x = 66**. Kiểm lại cả bốn ràng buộc bằng máy sau khi nới đất:
--   (a) trong nền đất mới, cách mép đông 19m — không sát mép;
--   (b) cách quỷ đá @47 **19m** > aggro 6 + 6 = 12 ✓ (V29);
--   (c) cách quỷ đá 19m > leash 10 + tầm đánh 2 = 12 ✓ (V32 — đứng lại đánh không kéo thêm con nào);
--   (d) ngoài vùng an toàn, ngoài mọi bệ nhảy ✓.
--
-- =============================================================================================
-- MODEL RIÊNG, KHÔNG MƯỢN — luật đã siết từ V31/V34
-- =============================================================================================
-- Bản nháp của migration này cho loài mới mượn `mob_wandering-bandit` và định ghi nợ theo tiền lệ
-- V23 §3 / V30 §1 (ship loài trước, model mượn, nợ sống trong CI). **Tiền lệ đó đã hết hiệu lực**:
-- `PostgresMonsterCatalogAdapterIntegrationTest.shouldNotLetAnyTwoSpeciesShareAModelKey` nay cấm
-- hẳn việc hai loài dùng chung `model_key`, và javadoc của chính ô đó viết sẵn cách xử lý đúng —
-- "dựng art rồi thêm migration đổi `model_key`, mất khoảng một giờ, V31/V34 đều đã làm đúng vậy
-- trong cùng ngày". Nên loài này có `.glb` riêng ngay từ đầu: `mob_blood-sand-scout`.
--
-- Nó là NGƯỜI (đệ tử Huyết Sa) trong khi năm model quái cũ có bốn là máy móc đá và một là tán tu,
-- nên art phải tự đứng được ở cỡ 64px chứ không chỉ khác màu — cùng luật đảo bố cục sáng-tối đã
-- dùng cho `archive_warden` vs `stone_ogre`.
--
-- =============================================================================================
-- CÂN BẰNG: hp 100 · atk 26 · def 10 · res 8 — chốt sau BA lượt playtest (150 → 120 → 100)
-- =============================================================================================
-- Tính bằng công thức `hit = round(atk × 100 / (100 + def))`, cả hai bên chung nhịp 1 giây, với
-- nhân vật nền + trọn bộ "Tạp Dịch" (ATK 37 · DEF 27 · HP 118) — cùng khuôn bảng V30 §2:
--
-- ⚠️ **BẢN ĐẦU CỦA MIGRATION NÀY KHÔNG QUA ĐƯỢC, VÀ PLAYTEST ĐÃ BÁC NÓ.** Số đo (hai lượt, trùng
-- khít): đi từ vùng an toàn ra x=66 mất **65 máu** (tán tu 17 + tán tu 17 + **quỷ đá 31**), tới nơi
-- còn **53/118**. Với máu đó, kẻ canh cần 3 nhát để hạ người chơi trong khi người chơi cần 5 nhát
-- để hạ nó ⇒ **thua chắc**, không phải vì chơi dở. Con quái tự nó thì cân đúng (5 đổi 6 khi máu
-- đầy); thứ bị bỏ sót là QUÃNG ĐƯỜNG — đúng cái bẫy V32 từng gặp, và cũng đúng 65 máu.
--
-- **Chủ dự án chọn: dời quỷ đá khỏi đường đi** (mục 6 dưới đây) + hạ máu kẻ canh 150 → 120.
-- (Và lượt đo TIẾP THEO bắt hạ thêm 120 → 100 — xem khối ⚠️ ngay dưới bảng.) Sau đó:
--
--   đường đi còn 2 tán tu × 17 = **34 máu** ⇒ tới hang với **84/118**
--
-- ⚠️ VÀ LƯỢT ĐO THỨ HAI BÁC TIẾP, vì một yếu tố chưa chỗ nào trong dự án ghi lại:
-- **quái ra đòn TRƯỚC người chơi hai nhát**. `aggro_radius_meters = 6` còn
-- `CombatConstants.ATTACK_RANGE_METERS = 2`, nên người chơi phải băng **4 mét dưới làn đạn** trước
-- khi đánh trả được nhát nào. Đo thật: quái đánh ở t=12,2s và t=13,8s; nhát đầu của người chơi mãi
-- t=14,5s. Với quái đánh 20 thì hai nhát phủ đầu đó là **40 máu mất trắng**, và nó áp cho MỌI trận
-- trong game chứ không riêng con này — chỉ là ở đây nó đủ lớn để lật kết quả.
--
--   ⇒ máu hiệu dụng lúc bắt đầu đánh trả: 84 − 40 = **44** ⇒ người chơi sống thêm 2 nhát nữa,
--     tức tổng cộng chỉ kịp **3 nhát** (102 sát thương). Máu 120 vì thế vẫn THUA (đo: quái còn 18).
--   ⇒ **máu 100**: 3 nhát × 34 = 102 ≥ 100 ⇒ thắng, và thắng đúng ở nhát cuối cùng có thể.
--
--   | Đối thủ                          | người chơi cần | quái cần | kết quả          |
--   |----------------------------------|----------------|----------|------------------|
--   | tán tu (quest 2/6/7)             | 3 nhát         | 7 nhát   | thắng thoải mái  |
--   | thạch vệ (quest 3)               | 4 nhát         | 6 nhát   | thắng, sát hơn   |
--   | **lính canh Huyết Sa (quest 8)** | **3 nhát**     | **5 nhát** | **thắng ở nhát CUỐI** |
--   | quỷ đá (chưa quest nào dùng)     | 7 nhát         | 4 nhát   | thua             |
--
-- (Bảng này tính theo máu ĐẦY như ba quest trước để so sánh được; con số quyết định của quest 8 là
--  84 máu lúc tới nơi trừ 40 máu phủ đầu — xem khối ⚠️ ngay trên.)
--
-- (Người chơi đánh 34/nhát vào def 10; kẻ canh đánh 26 thô = **20 sau giáp 27** — cả hai số đều ĐO
-- được trong playtest, không phải suy từ công thức.)
--
-- Ý đồ giữ nguyên: trận của nhịp twist phải là trận sát nút nhất arc 2 mà VẪN thắng được bằng đồ
-- arc 1 — ai mua nổi đồ Ngoại Môn thì thắng thoải mái, và đó là phần thưởng cho việc đã cày.
-- ---------------------------------------------------------------------------------------------

-- ---------------------------------------------------------------------------------------------
-- 1. Vật chứng — Huyết Sa Lệnh Phù. Vật tiêu hao (giao xong là mất) nên `max_stack` > 1 theo luật
--    đã khoá trong `ItemTemplate`; KHÔNG `use_effect`, KHÔNG `equip_slot` — nó là vật chứng.
-- ---------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack)
SELECT 'blood_sand_talisman', 'item.blood_sand_talisman.name', 10
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'blood_sand_talisman');

-- ---------------------------------------------------------------------------------------------
-- 2. Bảng rơi riêng của loài (khuôn V23 §2 — template trỏ tới `drop_table` bằng FK).
--    Rơi 100%, đúng 1 cái: cùng lý do đã ghi ở V38 — mục tiêu "mang vật về" mà tỉ lệ < 100% thì số
--    trận phải đánh thành biến ngẫu nhiên, và phần thưởng tính theo quãng đường hết nghĩa.
-- ---------------------------------------------------------------------------------------------
INSERT INTO drop_table (code)
SELECT 'blood_sand_scout'
 WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'blood_sand_scout');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'blood_sand_scout'
   AND it.code = 'blood_sand_talisman'
   AND NOT EXISTS (
       SELECT 1 FROM drop_table_entry e
        WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

-- ---------------------------------------------------------------------------------------------
-- 3. Loài mới — Thám Tử Huyết Sa, `model_key` RIÊNG (xem mục MODEL ở đầu file).
--    Tham số AI giữ mặc định (aggro 6 / leash 10) — cùng quyết định hoãn của ADR-037 D7.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, def, res, exp_reward, drop_table_id)
SELECT 'blood_sand_scout',
       'hud.monster.blood_sand_scout',
       'mob_blood-sand-scout',
       7,
       100,
       26,
       10,
       8,
       70,
       (SELECT id FROM drop_table WHERE code = 'blood_sand_scout')
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'blood_sand_scout');

-- ---------------------------------------------------------------------------------------------
-- 4. Điểm spawn — x = 66, cửa hang. Bốn ràng buộc hình học đã kiểm bằng máy (xem đầu file).
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y)
SELECT mt.id, 'main', 66, 0
  FROM monster_template mt
 WHERE mt.code = 'blood_sand_scout'
   AND NOT EXISTS (
       SELECT 1 FROM monster_spawn ms
        WHERE ms.monster_template_id = mt.id AND ms.world_code = 'main');

-- ---------------------------------------------------------------------------------------------
-- 5. Quest 8. Tiên quyết `missing_disciple_trail` (ADR-044, mốc CLAIMED): chỉ sau khi đã lần ra
--    manh mối thì mới biết đường mà đi. Tra theo `code`, không hardcode id.
--
--    CÂN BẰNG PHẦN THƯỞNG (chi tiết trong script): 1.600 EXP — quãng đường ~61m một chiều (dài
--    nhất từ trước tới nay) + trận sát nút nhất từ trước tới nay.
-- ---------------------------------------------------------------------------------------------
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'abandoned_cave',
       'MAIN',
       '/scripts/quest/abandoned_cave.js',
       (SELECT id FROM quest_template WHERE code = 'missing_disciple_trail')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'abandoned_cave');

-- ---------------------------------------------------------------------------------------------
-- 6. DỜI QUỶ ĐÁ 47 → 80 — hệ quả trực tiếp của playtest, không phải dọn dẹp tiện tay.
--
--    Quỷ đá đứng đúng giữa đường ra hang và một mình nó lấy **31 máu** mỗi lượt đi ngang (đo được:
--    nó là con đánh đau nhất bản đồ, atk 40). Dời nó ra SAU hang thì đường đi chỉ còn hai tán tu.
--
--    Dời được vì nó là loài **DUY NHẤT chưa quest nào dùng** (V30 §2 đã ghi đúng câu đó khi chọn
--    thêm `archive_warden` thay vì dùng lại nó) — không quest nào đếm nó, không script nào nhắc nó.
--
--    x = 80 kiểm lại cả bốn ràng buộc: cách hang @66 **14m** > aggro 6+6 = 12 ✓ và > leash 10 +
--    tầm đánh 2 = 12 ✓; trong nền đất mới `[-55, 85]` ✓; ngoài vùng an toàn và mọi bệ nhảy ✓.
--    (x=78 TRƯỢT: cách hang đúng 12, luật đòi > 12.)
--
--    ⚠️ Người chơi muốn săn quỷ đá vẫn tới được — nó chỉ lùi ra sau cái hang, không biến mất.
-- ---------------------------------------------------------------------------------------------
UPDATE monster_spawn
   SET pos_x = 80
 WHERE world_code = 'main'
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'stone_ogre')
   AND pos_x = 47;
