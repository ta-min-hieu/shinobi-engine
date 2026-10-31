-- V106 — Thiên Kiếp (ADR-126, lô 23). Số kiếp đã qua theo nhân vật; trần cấp = 18 × (kiếp + 1), qua đủ 5 kiếp thì
-- lên tới 100. Nhân vật ĐÃ vượt mốc trước khi có luật này được tính là đã qua các kiếp bên dưới cấp hiện tại — không
-- ai bị tụt cấp hay kẹt lại vì một luật mới.
ALTER TABLE character ADD COLUMN tribulations_passed INT NOT NULL DEFAULT 0
    CHECK (tribulations_passed BETWEEN 0 AND 5);

UPDATE character
   SET tribulations_passed = LEAST(5, GREATEST(0, (level - 1) / 18))
 WHERE level > 18;
