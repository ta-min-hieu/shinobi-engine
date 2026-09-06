-- V31 — `archive_warden` trỏ về model của chính nó. Trả nốt món nợ art mà V30 tự ghi ra.
--
-- V30 §2 cố ý cho loài mới mượn `mob_stone-ogre` và viết thẳng "NỢ NHÌN THẤY ĐƯỢC", đúng cách V23
-- đã làm với ba loài của ADR-037. Ngày 2026-09-06 art đã có: `mob_archive-warden`, dựng bằng
-- `kage_mesh_kit` (`tools/blender/build_monster_archive_warden.py`) — xem ASSET-LOG.md mục
-- 2026-09-06 và ART-PIPELINE §6.5.
--
-- ⚠️ Vì sao PHẢI có migration này chứ không phải "client tự suy ra" — y hệt V24: client chỉ suy
-- khoá từ `monster_code` (`MonsterModelKey`) khi `MonsterSpawnedEvent.model_key` RỖNG, mà server
-- thì luôn gửi giá trị của cột này. Chừng nào cột còn ghi `mob_stone-ogre` thì file .glb mới nằm
-- trên đĩa mà không con quái nào dùng: art đúng, code đúng, và trong game thạch vệ vẫn là một con
-- quỷ đá thứ hai.
--
-- Không đổi schema: đúng một câu UPDATE. `stone_ogre` giữ nguyên khoá của nó.

UPDATE monster_template SET model_key = 'mob_archive-warden' WHERE code = 'archive_warden';
