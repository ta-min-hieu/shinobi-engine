-- ADR-162 (2026-11-03) — Quét nhanh Bí Cảnh. Một dòng mỗi Bí Cảnh quét được: phí vàng, EXP nhận, bảng rơi tung một lần
-- (đồ đi bằng thư hệ thống — túi đầy không mất). Điều kiện: đã từng hạ boss (dungeon_first_clear); tốn MỘT lượt ngày
-- chung với lượt vào thật (dungeon_entry, ADR-099). Mọi con số là THAM SỐ (Điều 27) — PLACEHOLDER cân bằng.
CREATE TABLE dungeon_sweep_rule (
    dungeon_code    VARCHAR(64) PRIMARY KEY,
    gold_fee        BIGINT      NOT NULL CHECK (gold_fee >= 0),
    exp_reward      BIGINT      NOT NULL CHECK (exp_reward >= 0),
    drop_table_code VARCHAR(32) NOT NULL REFERENCES drop_table (code)
);

-- sample_boss cho 200 EXP khi hạ thật; quét = EXP boss + chừng ấy nữa cho quái dọc đường, không kể quái thường rơi gì.
INSERT INTO dungeon_sweep_rule (dungeon_code, gold_fee, exp_reward, drop_table_code)
VALUES ('sample_dungeon', 1000, 400, 'sample_boss');
