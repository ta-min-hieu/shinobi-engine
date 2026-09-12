-- V42 — QUEST THỨ CHÍN: "confront_the_steward" (Đối Chất Chấp Sự).
--
-- STORY.md §6 arc 2 (Ngoại Môn) — **CAO TRÀO**, chỗ arc 2 đóng. Nguyên văn:
--   *"hộ pháp Huyết Sa cải trang làm chấp sự tông môn — kẻ đứng sau các vụ mất tích. Đánh bại,
--    nhưng hắn kịp tự tan tu vi, manh mối đứt."*
--
-- =============================================================================================
-- MIGRATION NHẸ NHẤT CỦA CẢ CHUỖI, VÀ ĐÓ LÀ ĐIỀU ĐÁNG NÓI
-- =============================================================================================
-- File này chỉ có MỘT lệnh INSERT. Không loài mới, không điểm spawn mới, không vật phẩm, không
-- bảng rơi — vì V41 (ADR-048) đã mang con boss vào bảng dữ liệu rồi: hắn là một hàng
-- `monster_template` có `is_boss`, một hàng `monster_spawn` ở x=-12, và một file script AI.
--
-- Đó chính là thứ ADR-048 hứa: **boss thứ ba trở đi chỉ tốn dữ liệu**. Quest 9 là bằng chứng đầu
-- tiên của lời hứa đó, và nó tới ngay trong cùng ngày.
--
-- Tổng cộng đây là migration nội dung thứ TÁM liên tiếp không sửa một dòng Java nội dung nào
-- (V28 → V30 → V33 → V37 → V38 → V39 → V40 → V42). Điều 27 áp lần thứ tám.
--
-- =============================================================================================
-- HAI BƯỚC, VÀ VÌ SAO BƯỚC ĐẦU KHÔNG PHẢI "HỎI CHU HOÀNH"
-- =============================================================================================
--   1. `monster_killed` blood_sand_enforcer — đối chất, và hắn trở mặt (x=-12, V41)
--   2. `npc_talked` senior_sister           — báo lại: hắn tự tan tu vi, manh mối đứt
--
-- Quest này NHẬN TỪ CHU HOÀNH (`town_elder.js` nhánh `confront_steward`), nên một bước "nói
-- chuyện với Chu Hoành" sẽ không bao giờ đóng được bằng chính lần mở lời để nhận quest: sự kiện
-- `npc_talked` bắn ra TRƯỚC khi quest tồn tại. Quest 7 đã trả giá cho bài học đó — xem
-- `missing_disciple_trail.js`. Người giao là ông, nên bước đầu là việc phải đi làm.
--
-- Thứ tự sống trong SCRIPT (`progress.felled` gác bước 2), không sống trong Java (GAMEPLAY §9.6).
--
-- =============================================================================================
-- TIÊN QUYẾT: `abandoned_cave` (ADR-044, mốc CLAIMED)
-- =============================================================================================
-- Không phải để xếp hàng cho đẹp: lời thoại của Chu Hoành ở nhánh `confront_steward` NÓI VỀ cái
-- lệnh phù mà quest 8 lấy đi khỏi túi người chơi và đặt vào tay Tô Vân Nhi. Mở quest này trước khi
-- quest 8 đóng thì ông lão đang bình luận về một vật chưa ai tìm thấy.
--
-- Chuỗi đầy đủ tới đây:
--   report_to_outer_sect → recover_outer_sect_tallies → missing_disciple_trail
--     → abandoned_cave → **confront_the_steward**
--
-- =============================================================================================
-- 2.000 EXP ⇒ ĐÓNG QUEST Ở CẤP 15
-- =============================================================================================
--   Đường tối thiểu: 8.895 (hết quest 8) + 300 (chính hắn) + 2.000 = **11.195**.
--   `ExperienceCurve` tuyến tính 100 × cấp ⇒ tổng Cấp 15 = 10.500, Cấp 16 = 12.000 ⇒ **Cấp 15**.
--
-- Cao trào đẩy HAI cấp (13 → 15) trong khi mỗi quest thường đẩy một. Chủ đích: arc đóng thì phải
-- thấy được bằng con số, không chỉ bằng lời thoại. Và phần thưởng ở đây KHÔNG mua bằng quãng
-- đường — hắn đứng TRONG vùng an toàn `sect_grounds` [-15, 5] nên người chơi tới nơi máu đầy; cái
-- giá nằm trọn trong trận đấu (150 máu, atk 30, hai pha — xem V41 mục 2).
--
-- ⚠️ Con số cân bằng của trận đó vẫn là TRÊN GIẤY cho tới khi playtest. Quest 8 đã bác giấy ba lần
-- trong cùng một ngày. Nếu playtest bác, sửa SỐ chứ đừng sửa kỳ vọng.
-- ---------------------------------------------------------------------------------------------

INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'confront_the_steward',
       'MAIN',
       '/scripts/quest/confront_the_steward.js',
       (SELECT id FROM quest_template WHERE code = 'abandoned_cave')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'confront_the_steward');
