-- ADR-065 — bộ pháp khí (GDD §5 "Bộ pháp khí 4/6/8 món"). Mặc đủ N món KHÁC MẪU của cùng một bộ thì
-- cộng thưởng của bậc N; các bậc cộng dồn. Mọi con số là THAM SỐ (Điều 27) — cân bằng bằng SQL.
--
-- Hai bộ đầu tiên là đúng hai bậc trang bị đã có (V21): "Tập Sự" (novice_*) và "Ngoại Môn" (genin_*), mỗi
-- bộ 6 món (giáp, mũ, găng, giày, hai trang sức). Vũ khí KHÔNG thuộc bộ nào — chỉ số vũ khí đã là trục
-- chính của ATK, thêm thưởng bộ vào đó là nhân đôi một trục.

CREATE TABLE item_set (
    id        BIGSERIAL PRIMARY KEY,
    code      VARCHAR(64)  NOT NULL UNIQUE,
    name_key  VARCHAR(128) NOT NULL
);

-- Một mẫu thuộc nhiều nhất MỘT bộ (PK) — hai bộ tranh một món là thưởng đếm hai lần.
CREATE TABLE item_set_member (
    item_template_id  BIGINT PRIMARY KEY REFERENCES item_template (id),
    item_set_id       BIGINT NOT NULL REFERENCES item_set (id)
);

CREATE TABLE item_set_bonus (
    item_set_id      BIGINT NOT NULL REFERENCES item_set (id),
    pieces_required  INT    NOT NULL CHECK (pieces_required >= 1),
    bonus_atk        INT    NOT NULL DEFAULT 0 CHECK (bonus_atk >= 0),
    bonus_def        INT    NOT NULL DEFAULT 0 CHECK (bonus_def >= 0),
    bonus_res        INT    NOT NULL DEFAULT 0 CHECK (bonus_res >= 0),
    bonus_hp         INT    NOT NULL DEFAULT 0 CHECK (bonus_hp >= 0),
    PRIMARY KEY (item_set_id, pieces_required)
);

INSERT INTO item_set (code, name_key) VALUES
    ('novice', 'item_set.novice.name'),
    ('genin',  'item_set.genin.name');

INSERT INTO item_set_member (item_template_id, item_set_id)
SELECT it.id, s.id
  FROM item_template it JOIN item_set s ON s.code = 'novice'
 WHERE it.code IN ('novice_vest', 'novice_headband', 'novice_wraps',
                   'novice_sandals', 'novice_bracelet', 'novice_necklace');

INSERT INTO item_set_member (item_template_id, item_set_id)
SELECT it.id, s.id
  FROM item_template it JOIN item_set s ON s.code = 'genin'
 WHERE it.code IN ('genin_vest', 'genin_headband', 'genin_wraps',
                   'genin_sandals', 'genin_bracelet', 'genin_necklace');

-- Cỡ thưởng: trọn bộ ≈ 30–50% tổng chỉ số của chính 6 món đó (6 món Tập Sự cộng 3 ATK · 7 DEF · 7 RES
-- · 22 HP; trọn bộ thêm 1 · 2 · 2 · 10). Đủ để "gom đủ bộ" là một mục tiêu, không đủ để bộ thay cho cấp
-- trang bị kế tiếp.
INSERT INTO item_set_bonus (item_set_id, pieces_required, bonus_atk, bonus_def, bonus_res, bonus_hp)
SELECT s.id, v.pieces, v.atk, v.def, v.res, v.hp
  FROM item_set s
  JOIN (VALUES ('novice', 2, 0, 0, 0,  5),
               ('novice', 4, 0, 1, 1,  0),
               ('novice', 6, 1, 1, 1,  5),
               ('genin',  2, 0, 0, 0, 10),
               ('genin',  4, 0, 3, 3,  0),
               ('genin',  6, 3, 2, 2, 10)) AS v (code, pieces, atk, def, res, hp)
    ON v.code = s.code;
