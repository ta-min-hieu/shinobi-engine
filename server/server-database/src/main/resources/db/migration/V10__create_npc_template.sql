-- V10 — bảng npc_template (Phase 6 lát cắt 2, GAMEPLAY.md §9.2 / feature-dialogue).
-- dialogue_script_ref là đường dẫn classpath resource tới script hội thoại (feature-dialogue's
-- resources), tra qua ItemTemplatePort-style port (NpcTemplatePort.findDialogueScriptRefByCode) —
-- KHÔNG hardcode ID số (code là khoá nghiệp vụ ổn định).
CREATE TABLE npc_template (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name_key VARCHAR(64) NOT NULL,
    dialogue_script_ref VARCHAR(128) NOT NULL
);

-- NPC duy nhất hiện có (WorldFacade.SAMPLE_NPC_CODE = "town_elder", Phase 6 lát cắt 1) — vị trí
-- spawn VẪN hardcode trong Java (chưa có spawn-point DB cho NPC lẫn quái), bảng này chỉ phục vụ
-- tra dialogue_script_ref.
INSERT INTO npc_template (code, name_key, dialogue_script_ref)
VALUES ('town_elder', 'npc.town_elder.name', '/scripts/dialogue/town_elder.js');
