-- V30 — quest THỨ BA của cả dự án: "dọn góc Tàng Thư Đường bỏ hoang" (STORY.md §6 arc 1, GIEO MẦM).
--
-- CHỈ THÊM DỮ LIỆU. Không cột mới, không bảng mới, không ràng buộc mới — đúng thứ V28 đã chứng
-- minh là đủ cho một quest mới, và lần này chứng minh thêm cho một LOÀI QUÁI mới.
--
-- STORY.md §6 arc 1 có ba nhịp: việc vặt mở màn (`kill_training_dummies`, V11) → cao trào đêm trực
-- (`night_watch_bandit`, V28) → và nhịp GIEO MẦM: *"khi dọn dẹp một góc Tàng Thư Đường bỏ hoang,
-- người chơi nhặt được mảnh ngọc cổ không ai thèm lấy"*. V30 là nhịp thứ ba đó. Chu Hoành chỉ giao
-- việc dọn dẹp; mảnh ngọc là thứ người chơi tự nhặt được — arc 1 chỉ được coi nó là "vật may mắn"
-- (STORY.md §8 quy tắc 2), nên KHÔNG một câu thoại nào của trưởng lão nhắc tới nó.
--
-- `prerequisite_quest_id` trỏ về `night_watch_bandit` (ADR-044, mốc CLAIMED): người chơi phải quay
-- lại lĩnh thưởng phiên trực đêm rồi Chu Hoành mới có cớ giao việc trong tông. Tra theo `code` chứ
-- KHÔNG hardcode id — id do `GENERATED ALWAYS AS IDENTITY` cấp (cùng lý do đã ghi ở V28).
--
-- ⚠️ ADR-044 KHÔNG kiểm bắc cầu: nhận quest 3 chỉ kiểm quest 2 đã CLAIMED, không kiểm quest 1.
-- Đủ dùng vì muốn CLAIMED quest 2 thì đã phải qua quest 1 — nhưng ghi ra để không ai tưởng nó
-- kiểm cả chuỗi (Consequences của ADR-044).

-- ---------------------------------------------------------------------------------------------
-- 1. Bảng rơi của loài mới (khuôn V23 §2 — template trỏ tới `drop_table` bằng FK).
--
--    HAI entry, không hơn: `Inventory.add()` nay có gộp chồng (V27) nhưng túi vẫn 20 ô và mỗi
--    entry trúng vẫn có thể là một ô (cảnh báo gốc V21 §5).
--
--    Loài này rơi khá hơn tán tu một bậc, và đó là CHỦ ĐÍCH kinh tế: quest 3 mở ra nguồn vàng đầu
--    tiên đủ để tính tới bộ "Hạ Nhẫn" (10.900 vàng, V21 §4). Với tán tu (~12 vàng/mạng) thì bộ đó
--    xa tới mức không phải là mục tiêu; với thạch vệ (~50 vàng/mạng) thì nó là một chặng cày có
--    thật. Đây là con số CHƯA playtest, đúng thân phận mà V23 §3 đã ghi cho hai loài của nó.
-- ---------------------------------------------------------------------------------------------
INSERT INTO drop_table (code) VALUES ('archive_warden');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 45, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'archive_warden' AND it.code = 'iron_scrap';

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 35, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'archive_warden' AND it.code = 'chakra_shard';

-- ---------------------------------------------------------------------------------------------
-- 2. Loài mới `archive_warden` — "Thạch vệ Tàng Thư".
--
--    Lore: CÙNG DÒNG máy móc hộ mạch của Linh Triều với `stone_ogre` (STORY.md §7), bản nhỏ đặt
--    canh thư khố. Vô chủ từ sau Đại Kiếp nên nó canh cả người của tông.
--
--    ⚠️ CHỈ SỐ TÍNH BẰNG CÔNG THỨC, KHÔNG BẰNG CẢM GIÁC — phép tính đầy đủ ở
--    `OldArchiveDuelBalanceTest` (server-world), cùng khuôn `NightWatchDuelBalanceTest`. Tóm tắt,
--    với `hit = round(atk × 100 / (100 + def))` và cả hai bên chung nhịp
--    `ATTACK_INTERVAL_SECONDS = 1`:
--
--      Nhân vật nền 10/10/10/10 (ATK 30 · DEF 20 · HP 100, CombatConstants.deriveCombatStats)
--        + trọn bộ "Tạp Dịch" mua ở Tàng Bảo Các (700 vàng) ⇒ ATK 37 · DEF 27 · HP 118
--
--        | Đối thủ                    | người chơi cần | quái cần | kết quả          |
--        |----------------------------|----------------|----------|------------------|
--        | tán tu (quest 2), có đồ    | 3 nhát         | 7 nhát   | thắng thoải mái  |
--        | THẠCH VỆ (quest 3), có đồ  | 4 nhát         | 6 nhát   | THẮNG, sát hơn   |
--        | THẠCH VỆ, tay không        | 5 nhát         | 5 nhát   | hoà = chết       |
--        | quỷ đá (chưa quest nào)    | 7 nhát         | 4 nhát   | THUA             |
--
--    ⇒ Con này là bậc thang ĐÚNG cho quest 3: thắng được bằng đường chơi thường (bộ Tạp Dịch 700
--      vàng nằm trong tầm với sau quest 1+2), nhưng tay không thì hoà — tức là chết. `stone_ogre`
--      thì KHÔNG: hạ nó cần trọn bộ "Hạ Nhẫn" (10.900 vàng), xa hơn arc 1 rất nhiều. Đó là lý do
--      quest 3 KHÔNG dùng lại `stone_ogre` mà thêm một loài.
--
--    ⚠️ MODEL DÙNG CHUNG với `stone_ogre` (`mob_stone-ogre`) — NỢ NHÌN THẤY ĐƯỢC, y như V23 §3 đã
--    làm và ghi. Ở đây nó còn đỡ chối vì hai loài cùng một dòng máy móc Linh Triều, nhưng người
--    chơi vẫn thấy hai con giống hệt nhau cho tới khi có `.glb` riêng. Cột `model_key` tồn tại
--    CHÍNH ĐỂ làm được việc này mà không phải đổi code (V23 §1).
--
--    ⚠️ THAM SỐ AI GIỮ NGUYÊN MẶC ĐỊNH (aggro 6 / leash 10 / đuổi 3 / tuần tra 1,5 / bán kính 3 /
--    idle 3). Cho nó tính cách riêng nay chỉ tốn một câu UPDATE — nhưng đặt số CHƯA playtest cùng
--    lúc với việc thêm nội dung là đúng cái bẫy V25 §4 đã tránh. Cân bằng AI là một đợt riêng.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_template
    (code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res, hit_percent, exp_reward, drop_table_id)
SELECT 'archive_warden', 'hud.monster.archive_warden', 'mob_stone-ogre', 5, 120, 26, 0, 14, 10, 93, 55, dt.id
  FROM drop_table dt WHERE dt.code = 'archive_warden';

-- ---------------------------------------------------------------------------------------------
-- 3. Hai điểm spawn — LUẬT HÌNH HỌC CỦA V29 ÁP NGUYÊN VẸN.
--
--    Luật: hai điểm spawn CÓ aggro phải cách nhau HƠN tổng tầm aggro của cả hai, vì một điểm `p`
--    kéo được cả A lẫn B đúng khi `|A-B| <= aggroA + aggroB`. Thạch vệ giữ aggro mặc định 6, nên
--    mọi cặp phải cách nhau > 12m:
--
--      quỷ đá      47  (V29, giữ nguyên)
--      thạch vệ #1 47 + 13 = 60
--      thạch vệ #2 60 + 13 = 73
--
--    Kiểm cả ba cặp mới: |60-47| = 13 > 12 ✓ · |73-60| = 13 > 12 ✓ · |73-47| = 26 > 12 ✓.
--    Hàng rào máy móc là `PostgresMonsterCatalogAdapterIntegrationTest
--    .shouldKeepAggroSpawnsApartEnoughToPullOneAtATime` — nó duyệt MỌI cặp đọc từ DB, nên hai dòng
--    dưới đây bị soi lại tự động.
--
--    Vì sao ĐẶT XA NHẤT bản đồ: góc Tàng Thư Đường bỏ hoang nằm ngoài rìa tông môn, và người chơi
--    chỉ tới đó khi Chu Hoành sai — sau khi đã xong phiên trực đêm ở x=21. Đi từ gần ra xa cũng
--    đúng thứ tự độ khó: mộc nhân 8/14 → tán tu 21 → quỷ đá 47 (chưa quest nào) → thạch vệ 60/73.
--
--    ⚠️ Người chơi ĐI NGANG QUA quỷ đá (x=47) để tới đây, và quỷ đá thì họ chưa hạ nổi. Đó là chủ
--    đích: aggro 6m và leash 10m nên chạy qua là thoát, còn đứng lại đánh là chết — bài học "biết
--    con nào chưa đánh được" mà arc 1 nên dạy. Nếu playtest thấy quá phũ thì chỗ sửa là điểm spawn
--    (một migration mới), không phải chỉ số quái (bài học mục 57 của ROADMAP).
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 60, 0, 20 FROM monster_template mt WHERE mt.code = 'archive_warden';

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 73, 0, 20 FROM monster_template mt WHERE mt.code = 'archive_warden';

-- ---------------------------------------------------------------------------------------------
-- 4. Mảnh ngọc cổ — phần thưởng của quest 3, và là McGuffin xuyên truyện (STORY.md §6 arc 1).
--
--    `max_stack = 1` (di vật độc bản, khuôn trang bị), `equip_slot` NULL (mặc không được — arc 1
--    nó chỉ là "vật may mắn"; arc 3 mới phát sáng, arc 5 mới lộ là chìa khoá).
--
--    ⚠️ CỐ Ý KHÔNG có dòng `shop_item`. Bất biến của V21 §4 là "đồ RƠI ĐƯỢC thì bán được" (công cụ
--    load-test dựa vào nó) — mảnh ngọc không nằm trong bảng rơi nào, nên bất biến đó không đụng
--    tới. Và một di vật trục chính thì không nên có giá.
--
--    ⚠️ NỢ NHÌN THẤY ĐƯỢC: chưa có icon. `InventoryPanel` vẽ Ô TRỐNG khi thiếu vùng atlas (không
--    bịa hộp màu), nên nó hiện đúng TÊN mà không có hình cho tới khi art vẽ xong.
-- ---------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack)
SELECT 'ancient_jade_shard', 'item.ancient_jade_shard.name', 1
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'ancient_jade_shard');

-- ---------------------------------------------------------------------------------------------
-- 5. Quest 3. Nội dung (số lần giết, phần thưởng) nằm HẾT trong script (Điều 27) — bảng này chỉ
--    giữ định danh + đường dẫn script + điều kiện MỞ.
-- ---------------------------------------------------------------------------------------------
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'clear_old_archive',
       'MAIN',
       '/scripts/quest/clear_old_archive.js',
       (SELECT id FROM quest_template WHERE code = 'night_watch_bandit')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'clear_old_archive');
