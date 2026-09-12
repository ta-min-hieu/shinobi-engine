-- V43 — QUÁI ĐÁNH TRẢ KHI BỊ ĐÁNH (ADR-049, Accepted 2026-09-12).
--
-- =============================================================================================
-- VÌ SAO CẦN MỘT CỘT, TRONG KHI ĐÃ CÓ `aggro_radius_meters`
-- =============================================================================================
-- Playtest quest 9 (2026-09-12) đo được cao trào arc 2 KHÔNG đánh trả một nhát nào: người chơi hạ
-- chấp sự Hình Vân Sơn bằng 5 nhát / 8 giây / máu 118/118 nguyên vẹn, và trọn script hai pha của
-- hắn (`blood_sand_enforcer.js`) chưa chạy một dòng.
--
-- Chuỗi nguyên nhân, không mắt nào sai riêng lẻ:
--   1. hắn khai `aggro_radius_meters = 0` — CỐ Ý: hắn đứng giữa sân tông môn, trên hành lang mà
--      người chơi ARC 1 phải đi qua để tới thạch vệ @-20/-33 và sân khảo hạch @-47;
--   2. `AiSystem` đặt `TargetComponent` ở ĐÚNG MỘT CHỖ — vòng quét aggro;
--   3. `CombatSystem` chỉ ra đòn khi có mục tiêu, `BossAiSystem` chỉ chạy khi `AiState == ATTACK`.
--
-- ⇒ `aggro = 0` không chỉ nghĩa là "không chủ động gây hấn", nó nghĩa là **không bao giờ tự vệ**.
--
-- Ba cách chữa bằng DỮ LIỆU đều đã bị bác, mỗi cách bằng một phép đo:
--   · cho hắn aggro 2–3m ⇒ trục chơi MỘT chiều (ADR-033) nên "đi ngang" là đi xuyên qua đúng toạ
--     độ đó ⇒ người chơi arc 1 (Cấp ~8, ~100 máu) ăn đòn boss Cấp 12 atk 30;
--   · dời hắn ra chỗ vắng ⇒ không có chỗ vắng nào trên trục; phần đông vùng an toàn bị bệ nhảy
--     `[3,7]` và hai NPC (@-5, @2) chiếm;
--   · dựa vào vùng an toàn ⇒ **vùng an toàn KHÔNG chặn aggro**, nó chỉ hồi máu lúc bước vào
--     (ADR-046 D3) — chính playtest ấy đã chứng minh bằng một cái chết ở x=-12.
--
-- =============================================================================================
-- MẶC ĐỊNH `false`, VÀ ĐÓ LÀ PHẦN QUAN TRỌNG NHẤT CỦA MIGRATION NÀY
-- =============================================================================================
-- Cho MỌI quái đánh trả là **phá ADR-039 D4**: `training_dummy` được làm thụ động CÓ CHỦ ĐÍCH, và
-- "cọc luyện công đuổi đánh người chơi" đúng là con bug đã sửa 2026-09-04. Mặc định `false` nghĩa
-- là **không một loài nào đang chạy đổi hành vi** — kể cả 9 loài còn lại.
--
-- Các loài `aggro > 0` KHÔNG cần cột này bật: chúng đã tự đuổi theo người chơi trong tầm aggro rồi.
-- Loài duy nhất cần là loài CỐ Ý aggro 0 mà vẫn phải biết tự vệ.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE monster_template
    ADD COLUMN retaliates BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN monster_template.retaliates IS
    'ADR-049 — bị đánh thì đặt mục tiêu là kẻ vừa đánh và đuổi theo, kể cả khi aggro_radius = 0. '
    'Mặc định false: loài aggro > 0 không cần (đã tự đuổi), và cọc tập phải ở lại thụ động (ADR-039 D4).';

-- ---------------------------------------------------------------------------------------------
-- Loài DUY NHẤT bật ở đợt này: chấp sự Hình Vân Sơn — đứng yên giữa tông môn, không gây sự với ai
-- đi ngang, nhưng đối chất thì hắn trở mặt. Đó đúng là câu STORY §6 arc 2 mô tả.
-- ---------------------------------------------------------------------------------------------
UPDATE monster_template
   SET retaliates = true
 WHERE code = 'blood_sand_enforcer';
