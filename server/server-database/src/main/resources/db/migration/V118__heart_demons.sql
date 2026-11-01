-- ADR-146 (2026-11-01, lô 26) — Tâm Ma: từ kiếp thứ hai, sau đợt lôi kích thứ hai, chấp niệm của người độ kiếp hiện
-- hình thành một con quái trong đài Thiên Kiếp; lôi kiếp tạm ngừng tới khi hạ được nó. Bốn bậc theo số kiếp đã qua
-- (1..4 ⇒ người chơi đứng ở Cấp 36/54/72/90). Không EXP, không rơi đồ — đánh tâm ma không phải nơi luyện cấp.
-- Số chọn từ luật biên thật ở HeartDemonBalanceTest (máu lúc chạm mặt = máu sau hai đợt sét, không đan dược). Đề xuất
-- cân bằng. Model riêng mỗi bậc (`tools/blender/build_monster_heart_demon.py`): bóng tối của tu sĩ, ánh màu cảnh giới.
-- Tâm ma SĂN khắp đài (aggro = leash = 40 m): sét ngừng khi nó còn sống, nên nếu nó chỉ thủ chỗ thì người chơi đứng
-- xa sẽ hồi máu ngoài chiến đấu tới đầy rồi mới đánh. Đài là World một người, không có ai khác để nó lôi vào.
-- Loài cố ý không rơi gì vẫn phải có drop_table RỖNG cùng mã (quy ước V33).
INSERT INTO drop_table (code)
SELECT v.code FROM (VALUES ('heart_demon_1'), ('heart_demon_2'), ('heart_demon_3'), ('heart_demon_4')) AS v (code)
 WHERE NOT EXISTS (SELECT 1 FROM drop_table d WHERE d.code = v.code);

INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, exp_reward, drop_table_id, aggro_radius_meters, leash_radius_meters, retaliates)
SELECT v.code, v.name_key, 'mob_' || replace(v.code, '_', '-'), v.lvl, v.hp, v.atk, 0, v.def, v.res, v.hit, 0,
       (SELECT id FROM drop_table WHERE code = v.code), 40, 40, true
  FROM (VALUES ('heart_demon_1', 'hud.monster.heart_demon_1', 36, 240, 34, 38, 28, 96),
               ('heart_demon_2', 'hud.monster.heart_demon_2', 54, 300, 46, 50, 38, 97),
               ('heart_demon_3', 'hud.monster.heart_demon_3', 72, 380, 64, 64, 48, 98),
               ('heart_demon_4', 'hud.monster.heart_demon_4', 90, 420, 70, 72, 54, 98))
       AS v (code, name_key, lvl, hp, atk, def, res, hit)
 WHERE NOT EXISTS (SELECT 1 FROM monster_template mt WHERE mt.code = v.code);
