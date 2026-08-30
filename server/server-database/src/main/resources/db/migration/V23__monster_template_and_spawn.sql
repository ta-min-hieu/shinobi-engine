-- V23 — ADR-037 (Accepted 2026-08-30): quái đọc từ DB thay vì bốn chỗ hardcode.
--
-- Trước migration này tham số quái nằm rải ở BỐN chỗ: CombatConstants (chỉ số + máu + hồi sinh),
-- WorldFacade.MONSTER_SPAWN_X (vị trí), MonsterExperience (EXP, bảng tra Java), AiConstants (AI).
-- Và cả 4 điểm spawn đều tạo CÙNG một mã ⇒ overworld chỉ có MỘT loài quái, bốn bản sao.
--
-- ⚠️ CỘT QUAN HỆ, KHÔNG JSON — đi NGƯỢC đặc tả gốc ở DATABASE.md §2.2 (`base_stats_json`,
-- `ai_params_json`). Lý do đầy đủ ở ADR-037 D1 + Alternatives A: dự án không có thư viện JSON nào
-- trong TECHNOLOGY-STACK, và đã hai lần chọn ngược ở đúng ngã ba này (`drop_table_entry`, và
-- ADR-036 bác `stats_json`). DATABASE.md §2.2 phải sửa theo migration này, không phải ngược lại.
--
-- ⚠️ Tham số AI (aggro/leash/tốc độ đuổi) CỐ Ý chưa vào bảng — ADR-037 D7 hoãn sang đợt sau, mọi
-- loài vẫn dùng chung `AiConstants`. Boss (`sample_boss`) cũng giữ hardcode: nó có script GraalJS
-- và `BossAiSystem` riêng, kéo vào bảng là một quyết định khác.

-- ---------------------------------------------------------------------------------------------
-- 1. Schema
-- ---------------------------------------------------------------------------------------------
CREATE TABLE monster_template (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(32) NOT NULL UNIQUE,
    name_key      VARCHAR(64) NOT NULL,
    -- Khoá art (ADR-037 D5). Client hiện suy model bằng phép biến đổi thuần từ `code`
    -- (MonsterModelKey), nên cột này tồn tại ĐỂ hai loài khác nhau dùng chung một model trong lúc
    -- chờ art thật — nó đi ra client qua `MonsterSpawnedEvent.model_key`.
    model_key     VARCHAR(64) NOT NULL,
    level         INT NOT NULL DEFAULT 1,
    max_hp        INT NOT NULL,
    atk           INT NOT NULL,
    skill_dmg     INT NOT NULL DEFAULT 0,
    def           INT NOT NULL,
    res           INT NOT NULL,
    -- 0..100, chia 100 khi dựng CombatStats. INT chứ không REAL: cân bằng bằng số nguyên phần trăm
    -- dễ đọc hơn 0.9, và tránh so sánh dấu phẩy động trong migration sau này.
    hit_percent   INT NOT NULL DEFAULT 90,
    dodge_percent INT NOT NULL DEFAULT 0,
    crit_percent  INT NOT NULL DEFAULT 0,
    -- Khớp enum Element (NONE/HOA/THUY/PHONG/THO/LOI) — cùng khuôn `item_template.equip_slot`:
    -- 5 giá trị cố định không đáng một bảng tham chiếu + JOIN.
    element       VARCHAR(8) NOT NULL DEFAULT 'NONE',
    exp_reward    BIGINT NOT NULL DEFAULT 0,
    -- NULL = không rơi gì. FK này THAY cho quy ước ngầm hiện nay (`drop_table.code` trùng CHUỖI với
    -- monsterCode, chỉ được ghi bằng comment ở V4) — đổi tên một con quái là bảng rơi lặng lẽ không
    -- khớp nữa.
    drop_table_id BIGINT REFERENCES drop_table (id),

    CONSTRAINT monster_template_percent_range CHECK (
        hit_percent BETWEEN 0 AND 100
        AND dodge_percent BETWEEN 0 AND 100
        AND crit_percent BETWEEN 0 AND 100),
    CONSTRAINT monster_template_positive_hp CHECK (max_hp > 0)
);

CREATE TABLE monster_spawn (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monster_template_id BIGINT NOT NULL REFERENCES monster_template (id),
    -- 'main' = world chính. Dungeon/arena dựng với spawnOverworldContent=false và boss vào bằng
    -- lệnh riêng (SpawnBossCommand), nên chúng chưa đọc bảng này ở đợt này.
    world_code          VARCHAR(32) NOT NULL DEFAULT 'main',
    pos_x               REAL NOT NULL,
    -- ADR-033 khoá di chuyển vào trục X: nhân vật LUÔN ở y=0, nên quái đặt lệch y là quái người
    -- chơi không bao giờ chạm tới. CHECK biến ràng buộc đó từ comment thành luật.
    pos_y               REAL NOT NULL DEFAULT 0,
    respawn_seconds     REAL NOT NULL DEFAULT 10,

    CONSTRAINT monster_spawn_on_play_line CHECK (pos_y = 0),
    CONSTRAINT monster_spawn_positive_respawn CHECK (respawn_seconds > 0)
);

CREATE INDEX idx_monster_spawn_world_code ON monster_spawn (world_code);

-- ---------------------------------------------------------------------------------------------
-- 2. Bảng rơi cho hai loài mới (khuôn V4/V17/V21 — `drop_table.code` vẫn là khoá nghiệp vụ, nhưng
--    từ nay template trỏ tới nó bằng FK chứ không trùng chuỗi ngầm).
-- ---------------------------------------------------------------------------------------------
INSERT INTO drop_table (code) VALUES ('wandering_bandit'), ('stone_ogre');

-- Cướp lang thang: nguyên liệu + cơ hội nhỏ ra đồ Tập Sự.
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 40, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'wandering_bandit' AND it.code = 'iron_scrap';

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 8, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'wandering_bandit' AND it.code = 'novice_bracelet';

-- Quỷ đá: hiếm khi giết được nên rơi khá hơn. GIỮ SỐ ENTRY ÍT — Inventory.add() không gộp chồng và
-- túi chỉ 20 ô, mỗi entry trúng là một ô (cảnh báo gốc ở V21 §5 vẫn nguyên hiệu lực).
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 60, 1, 2
  FROM drop_table dt, item_template it
 WHERE dt.code = 'stone_ogre' AND it.code = 'chakra_shard';

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 10, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'stone_ogre' AND it.code = 'genin_sandals';

-- ---------------------------------------------------------------------------------------------
-- 3. Ba loài khởi điểm (ADR-037 D6).
--
--    ⚠️ `training_dummy` GIỮ NGUYÊN mọi con số đang chạy trong CombatConstants (50 máu, atk 8,
--    def 5, res 3, hit 90%, EXP 20). Không đợt nào được lặng lẽ đổi con quái mà mọi test đang dựa
--    vào — đây là hàng ĐỐI CHỨNG của cả migration này.
--
--    ⚠️ Cả ba dùng CHUNG model `mob_training-dummy`: art riêng chưa vẽ, và chủ dự án chọn "dùng lại
--    model cũ, ghi rõ là tạm" để việc cân bằng chạy được ngay thay vì chờ art. ĐÂY LÀ NỢ NHÌN THẤY
--    ĐƯỢC — hai loài mới trông y hệt hình nộm cho tới khi có `.glb` riêng.
--
--    ⚠️ Số của hai loài mới là ĐỀ XUẤT khởi điểm CHƯA playtest, đúng thân phận mà bảng tỉ lệ §7.1
--    và ENHANCE_STEP từng có. Ý đồ: với atk 40 của stone_ogre, tiến trình phòng thủ lần đầu đọc
--    được — không giáp chịu 3 đòn, bộ Hạ Nhẫn +0 chịu 6, +15 chịu 11 (so với hình nộm: 15/26/61,
--    ba con số đều là "không bao giờ chết").
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_template
    (code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res, hit_percent, exp_reward, drop_table_id)
SELECT 'training_dummy', 'hud.monster.training_dummy', 'mob_training-dummy', 1, 50, 8, 0, 5, 3, 90, 20, dt.id
  FROM drop_table dt WHERE dt.code = 'training_dummy';

INSERT INTO monster_template
    (code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res, hit_percent, exp_reward, drop_table_id)
SELECT 'wandering_bandit', 'hud.monster.wandering_bandit', 'mob_training-dummy', 3, 90, 22, 0, 10, 6, 92, 35, dt.id
  FROM drop_table dt WHERE dt.code = 'wandering_bandit';

INSERT INTO monster_template
    (code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res, hit_percent, exp_reward, drop_table_id)
SELECT 'stone_ogre', 'hud.monster.stone_ogre', 'mob_training-dummy', 6, 200, 40, 0, 25, 15, 95, 90, dt.id
  FROM drop_table dt WHERE dt.code = 'stone_ogre';

-- ---------------------------------------------------------------------------------------------
-- 4. Điểm spawn của world chính.
--
--    ⚠️ Bốn vị trí X đầu GIỮ NGUYÊN {8, 14, 21, 27} của WorldFacade.MONSTER_SPAWN_X — chúng đã được
--    chọn có lý do, ghi trong javadoc hằng số đó: xen kẽ với bệ nhảy demo (3-7, 9-13, 15-19) để vừa
--    đánh vừa leo, và cách điểm hồi sinh (0,0) xa hơn AGGRO_RADIUS_METERS (6m) để nhân vật vừa hồi
--    sinh không bị đánh ngay.
--
--    Khác biệt thật so với trước: hai trong bốn điểm đó giờ là LOÀI KHÁC, và có thêm quỷ đá ở x=34
--    — xa nhất, đúng tinh thần "mối đe doạ thật thì phải đi tìm".
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 8, 0, 10 FROM monster_template mt WHERE mt.code = 'training_dummy';

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 14, 0, 10 FROM monster_template mt WHERE mt.code = 'training_dummy';

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 21, 0, 15 FROM monster_template mt WHERE mt.code = 'wandering_bandit';

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 27, 0, 15 FROM monster_template mt WHERE mt.code = 'wandering_bandit';

INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', 34, 0, 30 FROM monster_template mt WHERE mt.code = 'stone_ogre';
