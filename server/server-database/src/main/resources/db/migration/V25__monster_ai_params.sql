-- V25 — ADR-039 (Proposed 2026-09-04): tham số AI theo LOÀI, trả nốt món nợ D7 của ADR-037.
--
-- ADR-037 đưa chỉ số/EXP/vị trí quái vào DB nhưng CỐ Ý hoãn tham số AI (D7), nên tới hôm nay
-- `AiConstants` (server-world) vẫn giữ 8 hằng số mà MỌI loài dùng chung: aggro 6m, leash 10m,
-- đuổi 3 m/s, tuần tra 1,5 m/s... AI.md §10 tự đánh dấu ⚠️ "CHƯA đúng" so với Điều 27.
--
-- ⚠️ CHỈ 6 TRONG 8 HẰNG VÀO ĐÂY. Hai hằng ở lại code có chủ đích (ADR-039 D1):
--   * ARRIVAL_THRESHOLD_METERS (0.3) — epsilon hình học "đã tới nơi chưa". Giá trị đúng là hàm của
--     FIXED_DT và tốc độ (60Hz × 3 m/s = 5cm/tick), không phải của tính cách loài. Đặt 0 ở DB thì
--     tickReturn KHÔNG BAO GIỜ thoả điều kiện tới nơi ⇒ quái rung quanh điểm spawn vĩnh viễn, và
--     không có gì báo lỗi.
--   * MAX_PATROL_DURATION_SECONDS (5) — VAN AN TOÀN chống kẹt. Van an toàn mà người dùng chỉnh
--     được thì không còn là van an toàn.
-- ⇒ AI.md §10 ("tham số AI là config") nói QUÁ; ADR-039 D1b thu hẹp nó và sửa tài liệu đó.
--
-- ⚠️ TƯƠNG TÁC ĐÃ BIẾT, không dựng CHECK: thời gian đi hết một lượt tuần tra là
--    patrol_radius_meters / patrol_speed_mps. Vượt MAX_PATROL_DURATION_SECONDS (5s, hằng Java) thì
--    quái không bao giờ TỚI điểm tuần tra — nó luôn hết giờ giữa đường rồi về Idle (hỏng lành).
--    Không viết CHECK cho ràng buộc này vì CHECK đó phải nhúng hằng số Java vào schema: hai nguồn
--    chân lý cho cùng một con số là thứ mục rữa nhanh nhất. Ba loài hiện tại đều an toàn
--    (3 / 1.5 = 2s < 5s).
--
-- 🔒 MIGRATION NÀY KHÔNG ĐỔI HÀNH VI (ADR-039 D2): mỗi DEFAULT bằng ĐÚNG giá trị hằng số đang chạy
--    trong AiConstants, nên ba hàng đã có nhận lại chính con số chúng đang dùng. Ngoại lệ DUY NHẤT
--    là §3 (`training_dummy`), và đó là chủ đích.

-- ---------------------------------------------------------------------------------------------
-- 1. Sáu cột "nội dung" — REAL vì đơn vị là MÉT và M/S, số thập phân là tự nhiên (khác nhóm
--    hit_percent của V23: phần trăm nguyên nên INT).
-- ---------------------------------------------------------------------------------------------
ALTER TABLE monster_template
    -- 0 = KHÔNG BAO GIỜ gây hấn (AiSystem có nhánh tường minh cho việc này, xem §3).
    ADD COLUMN aggro_radius_meters   REAL NOT NULL DEFAULT 6,
    -- Đo từ quái tới ĐIỂM SPAWN của nó (khác aggro — đo tới người chơi). Quá xa thì bỏ cuộc, Return.
    ADD COLUMN leash_radius_meters   REAL NOT NULL DEFAULT 10,
    ADD COLUMN chase_speed_mps       REAL NOT NULL DEFAULT 3,
    ADD COLUMN patrol_speed_mps      REAL NOT NULL DEFAULT 1.5,
    -- 0 = đứng NGUYÊN tại chỗ (FSM tự xử lý: điểm tuần tra trùng điểm spawn ⇒ tới nơi ngay).
    ADD COLUMN patrol_radius_meters  REAL NOT NULL DEFAULT 3,
    ADD COLUMN idle_duration_seconds REAL NOT NULL DEFAULT 3;

-- ---------------------------------------------------------------------------------------------
-- 2. CHECK gác những bất biến mà một cặp HẰNG SỐ CHUNG không bao giờ vi phạm được — nhưng dữ liệu
--    thì chỉ cách một lần gõ sai. Đây là cái giá thật của việc mở tham số ra cho DB, trả ngay.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE monster_template
    ADD CONSTRAINT monster_template_ai_non_negative CHECK (
        aggro_radius_meters >= 0
        AND patrol_radius_meters >= 0
        AND leash_radius_meters > 0
        AND chase_speed_mps > 0
        AND patrol_speed_mps > 0
        AND idle_duration_seconds > 0),

    -- Bất biến THẬT, không phải cho đẹp: nếu leash < aggro thì quái aggro một người chơi đứng ngoài
    -- tầm leash sẽ đuổi vài mét rồi lập tức Return, về tới nơi lại thấy người chơi vẫn trong tầm
    -- aggro ⇒ dao động Chase↔Return VÔ HẠN, quái rung tại chỗ, không bao giờ đánh được ai.
    ADD CONSTRAINT monster_template_ai_leash_covers_aggro CHECK (
        leash_radius_meters >= aggro_radius_meters),

    -- Chặn con quái tuần tra ra NGOÀI lãnh thổ của chính nó rồi aggro từ đó và bị leash ngay.
    ADD CONSTRAINT monster_template_ai_patrol_within_leash CHECK (
        patrol_radius_meters <= leash_radius_meters);

-- ---------------------------------------------------------------------------------------------
-- 3. `training_dummy` thành THỤ ĐỘNG (ADR-039 D4) — ngoại lệ có chủ đích DUY NHẤT của migration này.
--
--    Điều tra ADR-039: WorldFacade.spawnMonster gắn AiStateComponent + TargetComponent cho MỌI loài,
--    không có nhánh nào phân biệt; AiSystem khớp Family đó; AGGRO_RADIUS_METERS = 6 áp cho tất cả.
--    ⇒ cây "Mộc nhân luyện công" RỜI CỌC, đuổi theo người chơi 3 m/s và đánh trả (atk 8). Hành vi
--    này còn đang bị KHOÁ LẠI bằng test (WorldFacadeTest.shouldChaseAndAttackBack...) như thể nó là
--    đặc tả. Nó là di sản của Phase 4, khi thế giới có ĐÚNG MỘT loài quái nên "quái" và "hình nộm"
--    là cùng một thứ và không ai phải chọn. ADR-037 vừa phá vỡ điều kiện đó.
--
--    Hệ quả người chơi thấy: hai điểm spawn gần nhà (x=8, x=14) thành VÙNG LUYỆN TẬP AN TOÀN; nguy
--    hiểm thật bắt đầu từ cướp lang thang (x=21, 27) và quỷ đá (x=34). Chuỗi "chết → hồi sinh → bị
--    đuổi đánh lại" mà ADR-037 D2 ghi nhận biến mất với hai điểm gần nhất. Quest
--    kill_training_dummies.js vẫn chạy nguyên vẹn (người chơi vẫn đánh tay được), EXP vẫn 20.
--
--    🔁 GỠ BẰNG MỘT DÒNG nếu chủ dự án không đồng ý (trong một migration sau):
--       UPDATE monster_template SET aggro_radius_meters = 6, patrol_radius_meters = 3
--        WHERE code = 'training_dummy';
-- ---------------------------------------------------------------------------------------------
UPDATE monster_template
   SET aggro_radius_meters = 0,   -- không bao giờ gây hấn
       patrol_radius_meters = 0   -- đứng nguyên tại cọc, không lang thang 3m quanh gốc
 WHERE code = 'training_dummy';

-- ---------------------------------------------------------------------------------------------
-- 4. `wandering_bandit` và `stone_ogre` CỐ Ý giữ nguyên mặc định ở đợt này.
--
--    Cho hai loài đó tính cách riêng (cướp thấy xa + chạy nhanh, quỷ đá thấy gần + chậm + lãnh thổ
--    rộng) nay tốn ĐÚNG một câu UPDATE mỗi loài — đó chính là điều migration này mở khoá. Nhưng đặt
--    số CHƯA playtest vào cùng lúc với việc đổi hạ tầng là đúng thứ ADR-037 đã học được bài: bảng
--    D6 của nó chỉ đáng tin sau khi playtest khớp từng ô. Cân bằng là một đợt riêng, có số đo.
-- ---------------------------------------------------------------------------------------------
