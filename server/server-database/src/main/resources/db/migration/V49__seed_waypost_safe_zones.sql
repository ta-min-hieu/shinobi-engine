-- V49 — trạm tiếp ứng dọc trục (ADR-050).
-- MỘT cột mới + 6 hàng vùng an toàn + 1 NPC mẫu + 6 hàng đặt NPC. Không đổi bảng nào đang chạy,
-- không xoá gì, không đụng hàng `sect_grounds` của V35.
-- ============================================================================================
-- VÌ SAO CẦN — số đo, không phải cảm giác
-- ============================================================================================
-- Playtest 2026-09-17 (gameProbe, Kagemaru Cấp 30-31, bộ Trúc Cơ, máu tối đa 206):
--   · đi bộ `0 -> 150` tới nơi còn **51/206**; `0 -> 142` còn 83/206; `0 -> 145` còn 93 và 132.
--     Không con nào trong số đó là quái của quest — đó là THUẾ ĐƯỜNG ĐI: 11 điểm spawn nằm giữa
--     làng và x=175, mỗi con đánh đúng một nhát khi người chơi đi ngang.
--   · rồi trận chính bắt đầu ở mức máu đó. Ba trận 1v1 với `wasteland_beast` TỪ MÁU ĐẦY:
--     **1 thắng, 2 chết**, dù số học đúng thiết kế (ta 62 sát thương/nhát cần 5 nhát; nó 27 cần 8).
-- ============================================================================================
-- VÌ SAO KHÔNG SỬA ĐƯỢC BẰNG CÁCH ĐẶT VÙNG VÀO KHE TRỐNG
-- ============================================================================================
-- Tầm với thật của một con = patrol_radius (3) + aggro_radius (3 hoặc 6). Trải ra trên trục:
--   [87,99] [114,126] [128,140] [142,154] [152,170] [169,181] [182,194] [195,207]
-- Khe trống rộng nhất từ x=126 trở ra là **2 mét** — hẹp hơn chính tầm đánh (ATTACK_RANGE = 2m).
-- Nghĩa là nửa cuối trục KHÔNG có chỗ đứng hồi máu, nên hồi máu ngoài chiến đấu (ADR-046 D2:
-- 8 giây rồi 3%/giây) vô dụng ở đúng chỗ cần nó nhất. Vì vậy ADR-050 đổi LUẬT chứ không chỉ thêm
-- dữ liệu: vùng an toàn từ nay CHẶN AGGRO (D1) và quái đang đuổi thì bỏ cuộc ở biên vùng (D2).
-- Vùng an toàn nhờ LUẬT, không nhờ khoảng cách.
-- ============================================================================================
-- VÌ SAO THÊM CỘT `heals` — VÀ VÌ SAO KHÔNG THÊM `blocks_aggro`
-- ============================================================================================
-- Revisit Trigger của ADR-046 nói nguyên văn: "Khi thêm vùng an toàn thứ hai có luật khác thì
-- `safe_zone` cần cột `heals BOOLEAN`, đừng đoán trước bây giờ." Điều kiện đó vừa xảy ra.
--   · `sect_grounds` giữ heals = TRUE  — bước vào là đầy máu ngay (ADR-046 D3, không đổi).
--   · 6 trạm mới khai heals = FALSE — hồi đầy tức thì mỗi 30 mét biến mọi trận thành vô hại
--     (bước ra đánh, bước vào đầy máu, lặp lại). Thứ người chơi thiếu KHÔNG phải nút hồi máu, mà
--     là chỗ đứng yên 8 giây. Trạm trả lại chi phí THỜI GIAN: 206 máu mất ~23 giây.
-- Còn chặn aggro thì KHÔNG thành cột: cả hai loại vùng đều muốn nó, và STORY §7 phát biểu đúng
-- điều đó cho chính cái hub ("trận pháp hộ sơn — trong tông không thể động thủ"). Thêm cột để khai
-- một giá trị duy nhất là đoán trước luật thứ hai — đúng cái mà ADR-046 đã cấm, và đã đúng.
-- ============================================================================================
-- LUẬT ĐẶT TRẠM (ADR-050 D5) — THAY BẤT BIẾN CŨ
-- ============================================================================================
-- Bất biến cũ của `SafeZoneIntegrationTest` là "không quái nào với tới được bên trong vùng".
-- Nó KHÔNG thoả được ở đuôi trục, và từ D1 nó cũng không còn cần. Bất biến mới, vẫn kiểm bằng máy:
--   >>> Không vùng nào được chồng lên dải tuần tra của một điểm spawn (spawn ± patrol_radius) <<<
-- Lý do: quái ĐỨNG TRONG vùng an toàn là cảnh vô lý, và D1/D2 không đụng tới quái nên không có gì
-- đẩy nó ra. Dải tuần tra hiện có: 117-123, 131-137, 145-151, 158-164, 172-178, 185-191, 198-204.
-- Sáu biên dưới đây nằm trong các cửa sổ trống-tuần-tra (123,131) (137,145) (164,172) (191,198)
-- và hai khe trống hoàn toàn (75,87) (99,114).
-- ============================================================================================

ALTER TABLE safe_zone
    ADD COLUMN heals BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN safe_zone.heals IS
    'TRUE = bước vào hồi đầy máu ngay (ADR-046 D3). FALSE = chỉ che chở, người chơi tự hồi bằng '
    'hồi máu ngoài chiến đấu (ADR-050 D4). Chặn aggro thì MỌI vùng đều có, không khai ở đây.';

INSERT INTO safe_zone (code, world_code, min_x, min_y, max_x, max_y, heals)
SELECT * FROM (VALUES
        -- quãng 66-93: giữa thám tử Huyết Sa và đầu lĩnh đoạt mạch
        ('waypost_spirit_vein',      'main', 80.0,   -10.0, 86.0,   10.0, FALSE),
        -- quãng 93-120
        ('waypost_east_ridge',       'main', 104.0,  -10.0, 110.0,  10.0, FALSE),
        -- giữa hai tế đồ Huyết Sa
        ('waypost_dead_vein',        'main', 124.5,  -10.0, 129.5,  10.0, FALSE),
        -- trước đàn chủ Huyết Sa
        ('waypost_ringleader_ridge', 'main', 138.5,  -10.0, 143.5,  10.0, FALSE),
        -- TRƯỚC HAI YÊU NHÂN Lv29 — trạm quan trọng nhất của lát cắt Lv28-36
        ('waypost_wasteland_gate',   'main', 165.5,  -10.0, 170.5,  10.0, FALSE),
        -- trước cỗ khảo hạch Trúc Cơ Lv33, nhịp ĐÓNG arc 2
        ('waypost_trial_approach',   'main', 192.5,  -10.0, 197.5,  10.0, FALSE)
    ) AS incoming (code, world_code, min_x, min_y, max_x, max_y, heals)
 WHERE NOT EXISTS (SELECT 1 FROM safe_zone sz WHERE sz.code = incoming.code);

-- ============================================================================================
-- MỐC NHÌN THẤY ĐƯỢC (ADR-050 D6, chủ dự án chọn phương án (a))
-- ============================================================================================
-- Một vùng an toàn vô hình giữa Hoang Vực thì bằng không có. Phương án (a): dựng mốc bằng đúng
-- đường dữ liệu đang chạy (`npc_spawn`) — 0 opcode, 0 đổi protocol, client không phải vẽ gì mới.
--
-- MỘT `npc_template` dùng chung cho cả sáu trạm, không phải sáu NPC khác nhau:
--   · `dialogue_script_ref` là NOT NULL và `GatewayBootstrap.validateContentScripts` duyệt MỌI ref
--     lúc khởi động — ref hỏng là server KHÔNG BOOT. Sáu NPC riêng = sáu cây thoại phải viết và
--     phải nuôi, đổi lại chẳng thêm gì cho người chơi.
--   · `NpcModelKey` suy khoá art từ mã NPC (`waypost_attendant` -> `char_waypost-attendant`). Chưa
--     có file art thì `NpcPresenter` vẽ hộp cho riêng NPC đó — xấu, nhưng KHÔNG sập. Đó là nợ art
--     đã ghi vào ADR-050, không phải vật chặn.
-- Tường thuật (STORY §7 sẽ thêm dòng cùng PR): đệ tử ngoại môn trực trạm tiếp ứng, giữ một trận
-- pháp hộ thân cỡ nhỏ — cùng cơ chế với trận pháp hộ sơn của tông, thu nhỏ lại cho một chỗ nghỉ
-- chân. Arc 2 mới "lần đầu thấy RÌA Hoang Vực" (§6), nên trạm của tông ở rìa là hợp lore; sâu
-- trong Hoang Vực là chuyện của arc 4.
-- ============================================================================================

INSERT INTO npc_template (code, name_key, dialogue_script_ref)
SELECT 'waypost_attendant', 'npc.waypost_attendant.name', '/scripts/dialogue/waypost_attendant.js'
 WHERE NOT EXISTS (SELECT 1 FROM npc_template WHERE code = 'waypost_attendant');

-- Đặt ở TÂM mỗi vùng: người chơi đi tới chỗ thấy người đứng là đã ở trong vùng, không phải đoán
-- biên. pos_y = 0 vì `npc_spawn_on_play_line` CHECK (ADR-033).
INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)
SELECT nt.id, 'main', incoming.pos_x, 0
  FROM npc_template nt
 CROSS JOIN (VALUES (83.0), (107.0), (127.0), (141.0), (168.0), (195.0)) AS incoming (pos_x)
 WHERE nt.code = 'waypost_attendant'
   AND NOT EXISTS (
       SELECT 1 FROM npc_spawn ns
        WHERE ns.npc_template_id = nt.id AND ns.pos_x = incoming.pos_x);
