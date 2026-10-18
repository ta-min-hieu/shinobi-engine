-- V59 — NHIỆM VỤ NGÀY (ADR-055, chủ dự án duyệt 2026-10-18, lô 15).
--
-- 1. `repeat_policy`: 'ONCE' (mặc định — MỌI quest cũ, không đổi hành vi nào) hoặc 'DAILY'.
--    Quest DAILY đã CLAIMED mà `updated_at` (= lúc lĩnh, `markClaimedIfCompleted` ghi) TRƯỚC 0h hôm
--    nay giờ Asia/Ho_Chi_Minh thì được coi là CHƯA NHẬN — làm mới LƯỜI lúc đọc/nhận, không có cron.
--    Không thêm cột thời gian nào: `character_quest_progress.updated_at` đã có từ V11.
ALTER TABLE quest_template
    ADD COLUMN repeat_policy VARCHAR(8) NOT NULL DEFAULT 'ONCE'
        CONSTRAINT quest_template_repeat_policy_known CHECK (repeat_policy IN ('ONCE', 'DAILY'));

-- 2. Ba nhiệm vụ ngày ở Chu Hoành (STORY.md §7 "công việc tông môn"). Không tiên quyết — mở từ
--    Cấp 1. Nội dung (mục tiêu, phần thưởng) nằm HẾT trong script (Điều 27); bảng này chỉ giữ định
--    danh, đường dẫn script, và chính sách lặp. `type` = 'DAILY' cho rõ ý khi đọc bảng bằng mắt;
--    code chỉ đọc `repeat_policy`.
INSERT INTO quest_template (code, type, script_ref, repeat_policy)
SELECT v.code, 'DAILY', v.script_ref, 'DAILY'
  FROM (VALUES ('daily_dummy_drill', '/scripts/quest/daily_dummy_drill.js'),
               ('daily_mountain_patrol', '/scripts/quest/daily_mountain_patrol.js'),
               ('daily_water_duty', '/scripts/quest/daily_water_duty.js')) AS v (code, script_ref)
 WHERE NOT EXISTS (SELECT 1 FROM quest_template q WHERE q.code = v.code);
