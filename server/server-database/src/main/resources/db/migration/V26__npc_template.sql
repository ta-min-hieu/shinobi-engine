-- V26 — ADR-040 (Proposed 2026-09-03): NPC đọc từ DB thay vì hằng số trong `WorldFacade`.
--
-- ⚠️ `npc_template` KHÔNG được tạo ở đây — nó ĐÃ TỒN TẠI từ V10 (Phase 6 lát cắt 2). Chính comment
-- của V10 đã ghi chỗ thiếu: "vị trí spawn VẪN hardcode trong Java (chưa có spawn-point DB cho NPC
-- lẫn quái), bảng này chỉ phục vụ tra dialogue_script_ref". Migration này trả nốt nửa còn lại —
-- NPC có danh tính trong DB nhưng chưa có VỊ TRÍ.
--
-- Ba hằng số bị thay thế, cả ba trong `WorldFacade`: SAMPLE_NPC_CODE ("town_elder"),
-- SAMPLE_NPC_SPAWN_X (-5f), SAMPLE_NPC_SPAWN_Y (= CHARACTER_PLAY_LINE_Y = 0f).
--
-- ⚠️ RÀNG BUỘC BẮT BUỘC CỦA ADR-040 D4: sau migration này thế giới phải hành xử Y HỆT — một trưởng
-- lão Chu Hoành, ở (-5, 0), cùng cây hội thoại, cùng npc_code trên dây. Seed dưới đây là HÀNG ĐỐI
-- CHỨNG của cả đợt, đúng vai trò `training_dummy` giữ trong V23.

-- ---------------------------------------------------------------------------------------------
-- 1. Schema — bảng đặt NPC, tách khỏi bảng danh tính (ADR-040 D1)
-- ---------------------------------------------------------------------------------------------
-- Vì sao BẢNG RIÊNG chứ không thêm cột vào `npc_template` (Alternatives A của ADR-040): `code` là
-- UNIQUE và là danh tính TRÊN DÂY (TalkToNpcCommand.npc_code). Nhét pos_x/world_code vào đó khoá
-- cứng "danh tính ⇔ vị trí" thành 1:1 vĩnh viễn, nên cùng một chủ tiệm ở hai thị trấn buộc phải
-- nhân bản code + script + khoá i18n + model. Ngoài ra `npc_template` đang được feature-dialogue
-- đọc mỗi lần người chơi bấm nói chuyện — hàng đó là "NPC này nói gì", không nên mang toạ độ bản đồ.
CREATE TABLE npc_spawn (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    npc_template_id BIGINT NOT NULL REFERENCES npc_template (id),
    -- 'main' = world chính. Dungeon/arena dựng với spawnOverworldContent=false; hôm nay chúng bị
    -- chặn bằng một cờ boolean Java, từ nay bằng cùng cơ chế dữ liệu đã gác quái (V23).
    world_code      VARCHAR(32) NOT NULL DEFAULT 'main',
    pos_x           REAL NOT NULL,
    -- ADR-033 khoá di chuyển vào trục X: nhân vật LUÔN ở y=0. NPC là entity TĨNH (không đuổi theo
    -- như quái), nên đặt lệch y nghĩa là người chơi VĨNH VIỄN không tới đủ gần để tương tác.
    -- Javadoc SAMPLE_NPC_SPAWN_Y đã cảnh báo đúng điều này bằng lời; CHECK biến nó thành luật.
    pos_y           REAL NOT NULL DEFAULT 0,

    CONSTRAINT npc_spawn_on_play_line CHECK (pos_y = 0)
);

-- ⚠️ KHÔNG có `respawn_seconds` — khác `monster_spawn` MỘT CÁCH CÓ CHỦ ĐÍCH, không phải bỏ sót.
-- NPC không chết: `WorldFacade.spawnNpc()` nằm ngoài toàn bộ đường `tickMonsterRespawn` và comment
-- của nó ghi rõ "NPC tĩnh, không chết/hồi sinh — spawn 1 lần duy nhất, khác quái". Chép cột đó sang
-- cho "đối xứng" là đẻ ra một cột không ai đọc, tức một lời hứa sai với người đọc schema sau này.

CREATE INDEX idx_npc_spawn_world_code ON npc_spawn (world_code);

-- ---------------------------------------------------------------------------------------------
-- 2. Seed — ĐÚNG NPC đang chạy, ĐÚNG chỗ đang đứng (ADR-040 D4)
-- ---------------------------------------------------------------------------------------------
-- Không INSERT vào `npc_template`: hàng 'town_elder' đã có từ V10 và KHÔNG được đụng tới (đổi
-- name_key hay dialogue_script_ref ở đây là lặng lẽ đổi hành vi, đúng thứ D4 cấm).
--
-- ⚠️ CHỈ có `town_elder`. `shop_keeper` CỐ Ý chưa seed (ADR-040 D7) — nó chưa có script hội thoại,
-- và NPC không có `dialogue_script_ref` hợp lệ làm `ScriptValidator` chặn server ngay lúc khởi động
-- (SCRIPTING.md §8), chứ không phải "thừa một NPC". Nó cũng chưa có khoá i18n ở cả hai bundle và
-- chưa có `char_shop-keeper.glb`. Checklist đầy đủ ở ADR-040 "Việc cần làm tiếp theo" mục 4.
INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)
SELECT nt.id, 'main', -5, 0
  FROM npc_template nt
 WHERE nt.code = 'town_elder';
