-- V127 — Rơi đồ theo quẻ bói (ADR-158, lô 28, nối ADR-149 D4). Mỗi quẻ thêm % cơ hội tung bảng rơi THÊM một lượt cho
-- riêng người đó khi hạ quái, tới hết ngày. Mọi số là PLACEHOLDER (Điều 27).
ALTER TABLE fortune
    ADD COLUMN drop_bonus_percent INT NOT NULL DEFAULT 0 CHECK (drop_bonus_percent BETWEEN 0 AND 100);

UPDATE fortune SET drop_bonus_percent = v.pct
  FROM (VALUES ('dai_cat', 30), ('trung_cat', 15), ('tieu_cat', 5), ('binh', 0), ('hung', 0)) AS v (code, pct)
 WHERE fortune.code = v.code;
