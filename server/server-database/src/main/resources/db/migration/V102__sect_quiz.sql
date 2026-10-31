-- V102 — hỏi đáp tông môn (ADR-122, lô 23). Ngân hàng câu hỏi lore (STORY.md §1–§4, chỉ phần CÔNG KHAI — không câu
-- nào hé bí mật trục chính). Chữ câu hỏi/đáp án ở bundle i18n; `accepted` là các cách trả lời được chấp nhận, ĐÃ chuẩn
-- hoá (không dấu, chữ thường, cách nhau bằng '|') — server so câu chat đã chuẩn hoá với danh sách này.
CREATE TABLE quiz_question (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL UNIQUE,
    question_key VARCHAR(128) NOT NULL,
    answer_key   VARCHAR(128) NOT NULL,
    accepted     VARCHAR(512) NOT NULL CHECK (accepted <> '')
);

INSERT INTO quiz_question (code, question_key, answer_key, accepted) VALUES
    ('sect_name',       'quiz.sect_name.q',       'quiz.sect_name.a',       'thanh hu tong|thanh hu|thanh hu sect'),
    ('sect_master',     'quiz.sect_master.q',     'quiz.sect_master.a',     'huyen co chan nhan|huyen co'),
    ('continent',       'quiz.continent.q',       'quiz.continent.a',       'huyen thuong|dai luc huyen thuong'),
    ('first_realm',     'quiz.first_realm.q',     'quiz.first_realm.a',     'luyen khi|qi refining'),
    ('second_realm',    'quiz.second_realm.q',    'quiz.second_realm.a',    'truc co|foundation establishment'),
    ('realm_layers',    'quiz.realm_layers.q',    'quiz.realm_layers.a',    '9|chin|nine'),
    ('villain_sect',    'quiz.villain_sect.q',    'quiz.villain_sect.a',    'huyet sa giao|huyet sa|blood sand'),
    ('rival_sect',      'quiz.rival_sect.q',      'quiz.rival_sect.a',      'bach luyen kiem phai|bach luyen'),
    ('spirit_veins',    'quiz.spirit_veins.q',    'quiz.spirit_veins.a',    '5|nam|five'),
    ('mentor',          'quiz.mentor.q',          'quiz.mentor.a',          'chu hoanh|truong lao chu hoanh'),
    ('great_calamity',  'quiz.great_calamity.q',  'quiz.great_calamity.a', 'thien dia dai kiep|dai kiep'),
    ('sect_motto',      'quiz.sect_motto.q',      'quiz.sect_motto.a',      'tu chan truoc tu tam');
