-- V28 — quest thứ hai của arc 1: "đêm trực gặp tán tu cường đạo" (STORY.md §6 arc 1, cao trào).
--
-- Đây là quest THỨ HAI của cả dự án, và là quest thứ hai THEO DÕI GIẾT QUÁI. Nó chỉ chạy được vì
-- `ProcessMonsterKilledUseCase` đã bỏ hardcode một-mã-quest ngay trước migration này — điều kiện do
-- chính feature-quest/CLAUDE.md đặt ra: "khi có quest thứ 2 THEO DÕI GIẾT QUÁI, bắt buộc tổng quát
-- hoá tra cứu này trước, không copy-paste thêm hằng số hardcode thứ 2".
--
-- `prerequisite_quest_id` trỏ về `kill_training_dummies`: STORY.md §6 xếp "đập mộc nhân" là việc
-- vặt mở màn, "đêm trực" là cao trào — đi ngược thứ tự thì mất hẳn nhịp kể. Cột này có từ V11 nhưng
-- tới nay CHƯA dòng nào dùng; đây là lần đầu.
--
-- ⚠️ KHÔNG hardcode id: `kill_training_dummies` được cấp id bởi `GENERATED ALWAYS AS IDENTITY` ở
-- V11, nên phải tra theo `code` (khoá nghiệp vụ ổn định) thay vì viết số 1.
--
-- Nội dung (điều kiện hoàn thành, phần thưởng) nằm HẾT trong script, không nằm ở đây (Điều 27) —
-- bảng này chỉ giữ định danh + đường dẫn script.

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'night_watch_bandit',
       'MAIN',
       '/scripts/quest/night_watch_bandit.js',
       (SELECT id FROM quest_template WHERE code = 'kill_training_dummies')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'night_watch_bandit');
