-- V17 — drop_table cho boss mẫu (Phase 8 lát cắt 2, BossAiSystem/GAMEPLAY.md §6). Hạ tầng loot đã
-- có thật từ V4 (drop_table.code khớp thẳng monsterCode, chưa qua monster_template — xem comment
-- V4). "Phần thưởng đặc biệt" của boss = tỉ lệ rơi cao hơn + số lượng nhiều hơn training_dummy,
-- tái dùng item_template kunai có sẵn — KHÔNG cần item mới, KHÔNG cần code Java mới
-- (GrantLootUseCase đã tra theo monsterCode từ trước).
INSERT INTO drop_table (code) VALUES ('sample_boss');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 3, 6
  FROM drop_table dt, item_template it
 WHERE dt.code = 'sample_boss' AND it.code = 'kunai';
