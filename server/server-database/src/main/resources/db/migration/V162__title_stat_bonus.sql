-- ADR-210 (2026-11-07) — danh hiệu cộng chỉ số: danh hiệu ĐANG ĐEO cộng phẳng công / giáp / kháng / máu tối đa vào
-- chung đường chỉ số trang bị. Số là PLACEHOLDER cân bằng (đổi bằng SQL, khởi động lại server).
ALTER TABLE achievement
    ADD COLUMN bonus_atk INT NOT NULL DEFAULT 0 CHECK (bonus_atk >= 0),
    ADD COLUMN bonus_def INT NOT NULL DEFAULT 0 CHECK (bonus_def >= 0),
    ADD COLUMN bonus_res INT NOT NULL DEFAULT 0 CHECK (bonus_res >= 0),
    ADD COLUMN bonus_hp  INT NOT NULL DEFAULT 0 CHECK (bonus_hp >= 0);

UPDATE achievement SET bonus_atk = 2              WHERE code = 'slayer_10';
UPDATE achievement SET bonus_atk = 5              WHERE code = 'slayer_100';
UPDATE achievement SET bonus_atk = 10             WHERE code = 'slayer_1000';
UPDATE achievement SET bonus_hp = 30              WHERE code = 'level_10';
UPDATE achievement SET bonus_hp = 80              WHERE code = 'level_30';
UPDATE achievement SET bonus_def = 3              WHERE code = 'quests_10';
UPDATE achievement SET bonus_res = 2              WHERE code = 'duel_1';
UPDATE achievement SET bonus_atk = 4, bonus_res = 4 WHERE code = 'duel_10';
UPDATE achievement SET bonus_hp = 20              WHERE code = 'checkin_7';
UPDATE achievement SET bonus_atk = 6, bonus_def = 6 WHERE code = 'tournament_1';
