-- V41 — BOSS VÀO BẢNG DỮ LIỆU (ADR-048, Accepted 2026-09-12).
--
-- Điều kiện mở lại do chính ADR-037 D7 đặt ra đã xảy ra: *"Khi boss thứ hai xuất hiện ⇒ mở lại
-- 'boss giữ hardcode'."* Boss thứ hai là cao trào arc 2 (STORY.md §6) — hộ pháp Huyết Sa cải trang
-- làm chấp sự tông môn.
--
-- =============================================================================================
-- BỐN CỘT, VÀ CHỈ MỘT TRONG SỐ ĐÓ LÀ THỨ THẬT SỰ MỞ KHOÁ
-- =============================================================================================
-- Boss hôm nay hardcode ở BỐN chỗ, nhưng ba chỗ đầu chỉ là SỐ (`BossConstants`,
-- `CombatConstants.BOSS_STATS`, `WorldFacade.BOSS_SPAWN_X`) — đổi lúc nào cũng được. Chỗ thứ tư
-- mới chặn: `BossAiSystem` nạp ĐÚNG MỘT script lúc khởi tạo, nên boss thứ hai không có đường nào
-- khai hành vi riêng. `ai_script_ref` là cột gỡ đúng chỗ đó.
--
--   · `is_boss`                      — phân biệt loài có AI kịch bản với quái FSM thường
--   · `ai_script_ref`                — ĐƯỜNG DẪN script (không phải logic), khuôn
--                                      `npc_template.dialogue_script_ref` / `quest_template.script_ref`
--   · `ai_decision_interval_seconds` — nhịp HỎI script (throttle, AI.md §9)
--   · `ai_skill_cooldown_seconds`    — hồi chiêu đặc biệt
--
-- Hai cột nhịp là dữ liệu chứ không phải logic, và boss thứ hai cần đúng điều đó: một kẻ CẢI TRANG
-- ra đòn theo nhịp khác con máy móc `sample_boss`.
--
-- ⚠️ KHÔNG dựng bảng `boss_template` riêng (ADR-048 phương án B bị bác): boss dùng chung TOÀN BỘ 9
-- cột đang có. Boss khác quái ở HÀNH VI, không ở dữ liệu.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE monster_template
    ADD COLUMN is_boss BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN ai_script_ref VARCHAR(64),
    ADD COLUMN ai_decision_interval_seconds REAL NOT NULL DEFAULT 0.5,
    ADD COLUMN ai_skill_cooldown_seconds REAL NOT NULL DEFAULT 3.0;

-- Boss thì PHẢI có script, quái thường thì KHÔNG được có: cột này chỉ có nghĩa khi `is_boss`, và
-- một loài khai `is_boss = true` mà quên script sẽ chạy như quái FSM thường — im lặng, khó thấy.
-- Ràng buộc biến cái im lặng đó thành lỗi lúc ghi.
ALTER TABLE monster_template
    ADD CONSTRAINT monster_template_boss_needs_script
        CHECK ((is_boss AND ai_script_ref IS NOT NULL) OR (NOT is_boss AND ai_script_ref IS NULL));

-- ---------------------------------------------------------------------------------------------
-- 1. `sample_boss` CHUYỂN NGUYÊN TRẠNG (ADR-048 D5) — từng con số lấy thẳng từ hằng số Java:
--
--      CombatConstants.BOSS_MAX_HP = 320
--      CombatConstants.BOSS_STATS  = CombatStats(atk=3, skillDmg=0, def=10, res=8,
--                                                hit=0.95, dodge=0, crit=0.05)
--      BossConstants.BOSS_EXP_REWARD              = 200
--      BossConstants.BOSS_SCRIPT_RESOURCE         = /scripts/boss/sample_boss.js
--      BossConstants.DECISION_INTERVAL_SECONDS    = 0.5
--      BossConstants.HEAVY_STRIKE_COOLDOWN_SECONDS = 3.0
--      AiConstants.DEFAULTS = (aggro 6, leash 10, chase 3, patrol 1.5, patrolRadius 3, idle 3)
--
--    ⚠️ `atk = 3` KHÔNG phải lỗi gõ: sát thương của con này đến gần như toàn bộ từ CHIÊU trong
--    script (`raw_damage = base_atk × 1.8 … 2.5`), không phải từ đánh thường. Chuyển nguyên trạng
--    nghĩa là giữ cả sự kỳ quặc đó — ba test Phase 8 là hàng rào, nếu chúng đỏ thì việc chuyển sai,
--    KHÔNG phải chúng lỗi thời.
--
--    `model_key` = `mob_sample-boss`: đúng khoá mà `MonsterModelKey` phía client vẫn tự suy ra từ
--    trước tới nay, nên hành vi hiển thị không đổi — nhưng nay nó là DỮ LIỆU, và ngày ai đó dựng
--    `.glb` thì boss thôi là thực thể duy nhất còn vẽ bằng hộp mà không phải sửa dòng code nào.
--
--    KHÔNG thêm `monster_spawn` cho nó: `sample_boss` sống trong hầm ngục và vào bằng lệnh riêng
--    (`SpawnBossCommand`), không thuộc điểm spawn nào của world chính. Giữ đúng như vậy.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, dodge_percent, crit_percent, element, exp_reward,
        aggro_radius_meters, leash_radius_meters, chase_speed_mps,
        patrol_speed_mps, patrol_radius_meters, idle_duration_seconds,
        is_boss, ai_script_ref, ai_decision_interval_seconds, ai_skill_cooldown_seconds)
SELECT 'sample_boss', 'hud.monster.sample_boss', 'mob_sample-boss', 10, 320, 3, 0, 10, 8,
       95, 0, 5, 'NONE', 200,
       6, 10, 3, 1.5, 3, 3,
       true, '/scripts/boss/sample_boss.js', 0.5, 3.0
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'sample_boss');

-- ---------------------------------------------------------------------------------------------
-- 2. BOSS THỨ HAI — Hình Vân Sơn, chấp sự Hình Đường (STORY.md §6 arc 2, CAO TRÀO).
--
--    Hắn là kẻ đã KẾT LUẬN "đám đệ tử bỏ trốn" ở quest 7 — và đó chính là lý do hồ sơ đóng nhanh
--    như vậy. Quest 9 là chỗ người chơi mang lệnh phù (quest 8) tới đối chất và hắn trở mặt.
--
--    ⚠️ Tên riêng mới ⇒ STORY.md §8 quy tắc 3 đòi khai vào §4/§5 CÙNG lượt. §1–§6 là SSOT tường
--    thuật mà AI không được tự sửa, nên đây là dòng thứ hai của bảng chờ về tên riêng (cùng loại
--    với "Lý Tam" ở quest 7) — báo cáo, không tự thêm.
--
--    CÂN BẰNG — đặt cạnh kẻ canh hang (quest 8) vì người chơi tới đây ngay sau đó, cùng bộ đồ:
--
--      | | máu | atk | def | người chơi cần | hắn cần (từ 118 máu đầy) |
--      |---|---|---|---|---|---|
--      | kẻ canh hang (V40)   | 100 | 26 | 10 | 3 nhát | 6 nhát |
--      | **chấp sự (V41)**    | **150** | **30** | **12** | **5 nhát** | **5 nhát** |
--
--      Người chơi đánh 37 × 100/(100+12) ≈ **33**/nhát ⇒ 150/33 = 5 nhát.
--      Hắn đánh 30 × 100/(100+27) ≈ **24**/nhát ⇒ 118/24 = 5 nhát.
--      Cộng thêm CHIÊU (script, ×1.6 và ×2.2) — nên trên giấy đây là trận THUA nếu đánh tay đôi
--      sòng phẳng. Đó là chủ đích: hắn đứng TRONG tông môn, tức trong vùng an toàn `[-15, 5]`, nên
--      người chơi tới nơi với **máu đầy** và không mất một giọt nào dọc đường — khác hẳn quest 8.
--
--    ⚠️ **Cả phép tính này đều là TRÊN GIẤY.** Quest 8 đã chứng minh giấy sai hai lần (quãng đường,
--    và hai nhát phủ đầu do aggro 6 > tầm đánh 2). Số ở đây PHẢI qua playtest trước khi tin — và
--    nếu playtest bác, sửa số chứ đừng sửa kỳ vọng.
--
--    `aggro_radius_meters = 0` VÀ `patrol_radius_meters = 0` — PHẢI đủ cả hai. Bản đầu chỉ đặt
--    aggro = 0 rồi để patrol = 3 như mọi loài khác, và playtest cho thấy hậu quả: hắn **đi lang
--    thang khỏi chỗ đứng** (spawn ra ở x=-12,84 rồi trôi tới -9,64, cách người chơi 9,77m) nên
--    không đánh trúng được hắn. Một chấp sự đứng gác trong tông môn thì phải ĐỨNG YÊN — đúng
--    khuôn `training_dummy` (V25 §3), loài duy nhất khác cũng khai cả hai bằng 0.
--
--    `aggro_radius_meters = 0`: hắn KHÔNG tự gây hấn. Hắn là chấp sự đứng trong tông môn cho tới
--    khi người chơi đối chất — đúng khuôn `training_dummy` (V25 §3), và là thứ giữ cho người chơi
--    đi ngang không bị một NPC-quái đánh lén giữa sân tông môn.
--
--    ⚠️ `model_key` = `mob_blood-sand-enforcer`, KHÔNG mượn `mob_blood-sand-scout`. Bản nháp của
--    migration này mượn, và `shouldNotLetAnyTwoSpeciesShareAModelKey` đỏ đúng như javadoc của nó
--    hứa sẽ đỏ. Cách xử lý đúng ghi sẵn trong javadoc đó — dựng art rồi mới ship migration — nên
--    `.glb` riêng có trước, `tools/blender/build_monster_blood_sand_enforcer.py`. Hình hắn CỐ Ý
--    đọc ra "chấp sự tông môn" chứ không phải "kẻ địch": lộ mặt là việc của script, không phải
--    của model.
--
--    Nhịp AI: quyết định mỗi **0.4s** (nhanh hơn `sample_boss`) và chiêu hồi **4.0s** (chậm hơn) —
--    một kẻ cải trang đọc tình thế nhanh nhưng không thể ra chiêu Huyết Sa liên tục giữa tông môn
--    mà không lộ. Đây đúng là thứ D4 sinh ra để khai bằng dữ liệu.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, dodge_percent, crit_percent, element, exp_reward,
        aggro_radius_meters, leash_radius_meters, chase_speed_mps,
        patrol_speed_mps, patrol_radius_meters, idle_duration_seconds,
        is_boss, ai_script_ref, ai_decision_interval_seconds, ai_skill_cooldown_seconds)
SELECT 'blood_sand_enforcer', 'hud.monster.blood_sand_enforcer', 'mob_blood-sand-enforcer', 12, 150, 30, 0, 12, 10,
       95, 0, 5, 'NONE', 300,
       0, 10, 3, 1.5, 0, 3,
       true, '/scripts/boss/blood_sand_enforcer.js', 0.4, 4.0
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'blood_sand_enforcer');

-- ---------------------------------------------------------------------------------------------
-- 3. Điểm spawn — x = -12, trong sân tông môn.
--
--    ⚠️ TRONG vùng an toàn `sect_grounds` `[-15, 5]` (V35), và đó là CHỦ ĐÍCH chứ không phải sơ
--    suất: hắn đứng đó với tư cách CHẤP SỰ, không phải quái hoang. `aggro_radius_meters = 0` nghĩa
--    là hắn không bao giờ tự đánh ai, nên vùng an toàn không bị phá — người chơi đi ngang vẫn an
--    toàn tuyệt đối, đúng như đi ngang Chu Hoành hay Tô Vân Nhi.
--
--    Bốn ràng buộc hình học kiểm lại:
--      (a) trong nền đất `[-55, 85]` ✓
--      (b) luật giãn V29 KHÔNG áp (aggro 0 — cùng lý do hai con mộc nhân @8/@14 không tính) ✓
--      (c) hàng xóm có aggro gần nhất: thạch vệ @-20. ⚠️ **BẢN ĐẦU ĐẶT HẮN Ở x=-12 VÀ PLAYTEST
--          ĐÃ BÁC THẲNG** (2026-09-12): người chơi đi tới -12 thì bị thạch vệ đuổi theo đánh
--          chết — 6 đòn × 20 sát thương, 118 → 0, chưa kịp chạm vào chấp sự một nhát nào.
--          Lập luận sai nằm ở chỗ tôi viết "vùng an toàn `sect_grounds` [-15, 5] chặn nó".
--          **VÙNG AN TOÀN KHÔNG CHẶN AGGRO** — nó chỉ HỒI MÁU lúc bước vào (ADR-046 D3,
--          `TriggerZoneSystem`). Quái vẫn đuổi và vẫn đánh trong đó. Lần chạy ấy còn nhận
--          ĐÚNG 0 gói 408, tức cả phần hồi máu cũng không bù được gì.
--
--          Chuỗi số ĐO ĐƯỢC, thay cho lập luận: thạch vệ spawn -20, patrol ±3 ⇒ mép đông -17;
--          aggro 6 ⇒ phát hiện tới **-11**; leash 10 ⇒ đuổi tới **-10**. Người chơi đứng -12
--          cách mép đông -17 đúng 5m < 6m ⇒ bị phát hiện. Đó là toàn bộ nguyên nhân.
--
--          ⇒ Dời chấp sự về **x=-8**: cách mép phát hiện -11 ba mét, nên đứng đánh ở đó KHÔNG
--          bao giờ đánh thức thạch vệ; và kể cả nếu nó bị đánh thức bằng đường khác, chỗ xa
--          nhất nó tới được là -10, còn cách -8 hai mét. Vẫn trong vùng an toàn [-15, 5] và
--          cách trưởng lão Chu Hoành (@-5) ba mét.
--          📌 Luật "chỗ đánh nhau yên tĩnh" (ROADMAP mục 66) áp đúng ở đây: giãn theo AGGRO chỉ
--          đủ để ĐI NGANG, còn ĐỨNG LẠI ĐÁNH thì phải tính cả patrol lẫn leash của hàng xóm.
--      (d) ngoài mọi bệ nhảy (`[3,7] · [9,13] · [15,19]`) ✓
--
--    ⚠️ `respawn_seconds = 900` (15 phút) THAY VÌ mặc định 10 giây, và đây là chỗ cơ chế KHÔNG
--    khớp hẳn với tường thuật. STORY §6 viết *"đánh bại, nhưng hắn kịp tự tan tu vi, manh mối
--    đứt"* — tức hắn phải BIẾN MẤT. Nhưng world chính hồi sinh MỌI điểm spawn
--    (`WorldFacade.handleMonsterDeath`: `spawnIndex != null` ⇒ lên lịch hồi sinh), và
--    `CHECK (respawn_seconds > 0)` của V23 làm cho "không bao giờ hồi sinh" **không khai được**
--    bằng dữ liệu. 900 giây là thứ gần nhất có thể: đủ lâu để hắn không mọc lại giữa lúc người
--    chơi còn đứng đó báo tin cho Tô Vân Nhi, không đủ để gọi là đã giải quyết.
--    📌 Đây là khoảng cách CŨ của dự án chứ không phải nợ do ADR-048 sinh ra: cao trào arc 1
--    (`wandering_bandit`) cũng là một con quái hồi sinh thường. Đóng thật thì cần cơ chế "spawn
--    một lần / theo tiến độ quest", và cơ chế đó cần ADR riêng — báo cáo, không tự làm.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', -8, 0, 900
  FROM monster_template mt
 WHERE mt.code = 'blood_sand_enforcer'
   AND NOT EXISTS (
       SELECT 1 FROM monster_spawn ms
        WHERE ms.monster_template_id = mt.id AND ms.world_code = 'main');
