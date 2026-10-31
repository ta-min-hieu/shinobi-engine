-- V110 — NPC thợ rèn + thương nhân du hành (ADR-131, lô 24). Thợ rèn đứng TĨNH trong sân tông (vùng an toàn
-- [-15, 5], cạnh Chu Hoành @-5) và nhận lò luyện khí của ADR-120. Thương nhân là NPC ĐỘNG: chỉ có template, KHÔNG có
-- npc_spawn — server đặt nó cạnh trạm của lần ghé đang diễn ra (MerchantCommands.presenceJob).
INSERT INTO npc_template (code, name_key, dialogue_script_ref)
SELECT v.code, v.name_key, v.script
  FROM (VALUES ('sect_blacksmith',    'npc.sect_blacksmith.name',    '/scripts/dialogue/sect_blacksmith.js'),
               ('traveling_merchant', 'npc.traveling_merchant.name', '/scripts/dialogue/traveling_merchant.js'))
       AS v (code, name_key, script)
 WHERE NOT EXISTS (SELECT 1 FROM npc_template n WHERE n.code = v.code);

INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)
SELECT n.id, 'main', -11, 0 FROM npc_template n
 WHERE n.code = 'sect_blacksmith'
   AND NOT EXISTS (SELECT 1 FROM npc_spawn s WHERE s.npc_template_id = n.id);
