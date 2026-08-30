-- V24 — hai loài quái của ADR-037 trỏ về model của chính chúng.
--
-- V23 cố ý cho CẢ BA loài dùng chung `mob_training-dummy` vì art riêng chưa vẽ (ghi rõ trong chính
-- V23 §3: "ĐÂY LÀ NỢ NHÌN THẤY ĐƯỢC"). Ngày 2026-08-30 art đã có: `mob_stone-ogre` (sinh bằng
-- Text→3D, Hyper3D Rodin) và `mob_wandering-bandit` (dựng bằng kage_mesh_kit) — xem ASSET-LOG.md
-- mục 2026-08-30 và ART-PIPELINE §6.6.
--
-- ⚠️ Vì sao PHẢI có migration này chứ không phải "client tự suy ra": client chỉ suy khoá từ
-- `monster_code` (MonsterModelKey) khi `MonsterSpawnedEvent.model_key` RỖNG. Server luôn gửi giá
-- trị trong cột này, nên chừng nào cột còn ghi `mob_training-dummy` thì file .glb mới nằm trên đĩa
-- mà không con quái nào dùng — art đúng, code đúng, và trong game vẫn là ba cái cọc gỗ giống hệt
-- nhau. Đây đúng là cột `model_key` được thêm ĐỂ LÀM GÌ (ADR-037 D5).
--
-- Không đổi schema: chỉ hai câu UPDATE. `training_dummy` giữ nguyên — nó là hàng đối chứng của V23.

UPDATE monster_template SET model_key = 'mob_wandering-bandit' WHERE code = 'wandering_bandit';
UPDATE monster_template SET model_key = 'mob_stone-ogre'      WHERE code = 'stone_ogre';
