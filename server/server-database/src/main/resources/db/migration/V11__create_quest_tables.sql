-- V11 — bảng quest_template/character_quest_progress (DATABASE.md §2.5, Phase 6 lát cắt 3).
--
-- progress_json là TEXT ĐỤC (Java KHÔNG bao giờ parse nội dung) — script tự JSON.stringify/parse
-- lấy từ GraalJS built-in (đã xác nhận bằng test thật ở engine-scripting trước khi chọn cách này),
-- tránh phải thêm thư viện JSON phía Java (CLAUDE.md §4 — thêm dependency mới phải dừng hỏi).
--
-- KHÔNG có dòng character_quest_progress nghĩa là NOT_STARTED (status đó không cần lưu — giống
-- cách "chưa mặc đồ" không cần dòng equipment_slot).
CREATE TABLE quest_template (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    type VARCHAR(16) NOT NULL,
    script_ref VARCHAR(128) NOT NULL,
    prerequisite_quest_id BIGINT REFERENCES quest_template (id)
);

CREATE TABLE character_quest_progress (
    character_id BIGINT NOT NULL REFERENCES character (id),
    quest_template_id BIGINT NOT NULL REFERENCES quest_template (id),
    status VARCHAR(16) NOT NULL,
    progress_json TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, quest_template_id)
);

CREATE INDEX idx_character_quest_progress_status ON character_quest_progress (character_id, status);

-- Quest mẫu duy nhất (Phase 6 lát cắt 3) — giết training_dummy (quái duy nhất tồn tại, chưa có
-- monster_template spawn thật). Nhận qua dialogue town_elder (feature-dialogue, V10).
INSERT INTO quest_template (code, type, script_ref)
VALUES ('kill_training_dummies', 'MAIN', '/scripts/quest/kill_training_dummies.js');
