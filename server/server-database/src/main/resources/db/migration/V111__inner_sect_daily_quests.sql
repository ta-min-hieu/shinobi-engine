-- V111 — nhiệm vụ ngày nội môn (ADR-135, lô 25). Cùng cơ chế ADR-055 (`repeat_policy = 'DAILY'`), nhưng có TIỀN ĐỀ
-- `inner_sect_examination` (quest mở arc 3) — chỉ đệ tử đã qua khảo hạch nội môn mới nhận được. Số EXP/linh thạch nằm
-- trong script (Điều 27) và là ĐỀ XUẤT CÂN BẰNG.
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id, repeat_policy)
SELECT v.code, 'DAILY', v.script_ref, (SELECT id FROM quest_template WHERE code = 'inner_sect_examination'), 'DAILY'
  FROM (VALUES ('daily_inner_sparring', '/scripts/quest/daily_inner_sparring.js'),
               ('daily_gate_patrol', '/scripts/quest/daily_gate_patrol.js')) AS v (code, script_ref)
 WHERE NOT EXISTS (SELECT 1 FROM quest_template q WHERE q.code = v.code);
