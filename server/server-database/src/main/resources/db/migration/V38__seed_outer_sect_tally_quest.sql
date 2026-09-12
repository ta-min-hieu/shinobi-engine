-- V38 — quest THỨ SÁU: "Đòi Lại Thẻ Bài" (STORY.md §6 arc 2, nhịp VIỆC VẶT + GIEO MẦM twist).
--
-- CHỈ THÊM DỮ LIỆU. Không cột mới, không bảng mới, không ràng buộc mới — lần thứ NĂM liên tiếp
-- (V28 → V30 → V33 → V37 → V38) một quest mới không tốn một dòng Java NỘI DUNG nào. Cơ chế mà quest
-- này cần (mục tiêu "mang vật X tới NPC Y") đã được dựng TRƯỚC, ở tầng cơ chế, 2026-09-11 —
-- xem `ProcessQuestEventUseCase` và GAMEPLAY.md §9.5.
--
-- =============================================================================================
-- QUEST ĐẦU TIÊN DÙNG MỤC TIÊU LOẠI THỨ BA
-- =============================================================================================
-- Bốn quest đầu đếm `monster_killed`; quest 5 đặt cờ khi `npc_talked`. Quest này là quest đầu tiên
-- phải MANG MỘT VẬT về: giết tán tu để lấy thẻ bài, rồi giao tận tay Tô Vân Nhi. Đồ bị trừ khỏi túi
-- trong CÙNG transaction với lần ghi tiến độ, nên không có cửa sổ nào để mất đồ mà quest không xong,
-- hay ngược lại.
--
-- =============================================================================================
-- NHỊP CỐT TRUYỆN (STORY.md §8 quy tắc 1 + 2)
-- =============================================================================================
-- §6 arc 2 khai ba thứ theo thứ tự: (1) "đứng vững ở ngoại môn" + tài nguyên phát theo thứ hạng,
-- (2) twist "chuỗi đệ tử ngoại môn MẤT TÍCH — Hình Đường kết luận là bỏ trốn", (3) hang luyện công
-- bỏ hoang + dấu vết hút tu vi + cái tên Huyết Sa.
--
-- Quest này nằm ở (1) và chỉ GIEO MẦM cho (2). Cụ thể nó được phép và KHÔNG được phép:
--   ✅ việc vặt ngoại môn đầu tiên, có lý do tường thuật cho thứ hạng (§6 "Xung đột").
--   ✅ một trong hai thẻ bài mang tên một đệ tử "mấy hôm nay không thấy về" — và lời giải thích
--      chính thức, đúng như §6 viết, là ĐỨA ĐÓ BỎ TRỐN. Người chơi chưa có lý do gì để nghi ngờ.
--   ❌ KHÔNG một chữ nào về hang luyện công, hút tu vi, hay Huyết Sa — đó là nhịp (3), chưa tới.
--   ❌ Tô Vân Nhi KHÔNG được tỏ ra biết nhiều hơn mình nói (twist arc 5: cô là tai mắt bị ép buộc).
--      Ở đây cô thật lòng tin lời Hình Đường, và hơi bực vì chuyện đó làm cả ngoại môn mang tiếng.
--
-- =============================================================================================
-- CÂN BẰNG: 2 thẻ bài, rơi 100%, 1.000 EXP
-- =============================================================================================
-- * **Rơi 100%** chứ không phải một tỉ lệ nào khác. Mục tiêu "mang về 2 cái" mà tỉ lệ rơi < 100%
--   thì số trận người chơi phải đánh là một biến ngẫu nhiên, và phần thưởng tính theo quãng đường
--   + số nhát chịu đòn (khuôn V30 §2) lập tức hết nghĩa. Tán tu là loài CƯỚP ĐƯỜNG (V29); thứ
--   chúng vừa cướp của đệ tử ngoại môn thì con nào cũng đang giữ.
-- * **2 cái** = đúng hai điểm spawn tán tu đang có (x=21 và x=34). Không thêm điểm spawn nào: mọi
--   ràng buộc hình học (vùng an toàn V35, giãn theo aggro V29, leash V32, bệ nhảy demo) đã được
--   thoả sẵn bởi bố cục hiện tại, và thêm một điểm spawn là mở lại cả bốn ràng buộc đó.
-- * **1.000 EXP**, chọn từ tương quan chứ không chọn rồi mới kiểm:
--
--     quest 2  đuổi 1 tán tu, đi 21m                    =   800 EXP
--     quest 3  hạ 2 thạch vệ (dai gấp rưỡi), đi 33m     = 1.200 EXP
--     quest 6  hạ 2 tán tu, đi 34m — CÙNG loài quest 2,
--              nhưng người chơi nay Cấp 10 + có trang bị =  1.000 EXP
--
--   Đường chơi tối thiểu sau quest này: 4.585 (hết arc 1) + 200 (quest 5) + 2 × 35 (tán tu)
--   + 1.000 = **5.855**. `ExperienceCurve` tuyến tính 100 × cấp ⇒ tổng của Cấp 11 là 5.500, Cấp 12
--   là 6.600 ⇒ người chơi đóng quest này ở **Cấp 11**. Đó là ý đồ: quest arc 2 đầu tiên phải đẩy
--   được đúng MỘT cấp, để quãng Lv10–18 bắt đầu có đường đi thay vì đứng yên.
--
-- ⚠️ CÒN TREO — DÒNG #19 CỦA BẢNG CHỜ, QUEST NÀY KHÔNG ĐÓNG NÓ. STORY §6 xếp arc 2 ở Lv19–36,
--    mà người chơi bước vào arc 2 ở Cấp 10 và đóng quest này ở Cấp 11. Quest này thu hẹp khoảng
--    lệch đúng một cấp; lấp trọn quãng Lv10–18 vẫn là quyết định thiết kế của chủ dự án (nhiệm vụ
--    ngày? thêm quest? đổi đường cong EXP?), và AI không tự chọn hộ (CLAUDE.md §1).
-- ---------------------------------------------------------------------------------------------

-- ---------------------------------------------------------------------------------------------
-- 1. Vật phẩm quest — Thẻ Bài Ngoại Môn
--
--    `max_stack` = 10: đây là vật tiêu hao (giao xong là mất), nên theo luật đã khoá trong
--    `ItemTemplate` nó phải > 1. 10 vì quest cần 2 và không nội dung nào hiện cần hơn.
--    KHÔNG `use_effect` (bấm Dùng không làm gì) và KHÔNG `equip_slot` — nó là vật chứng, không
--    phải đan dược cũng không phải trang bị.
-- ---------------------------------------------------------------------------------------------
INSERT INTO item_template (code, name_key, max_stack)
SELECT 'outer_sect_tally', 'item.outer_sect_tally.name', 10
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'outer_sect_tally');

-- ---------------------------------------------------------------------------------------------
-- 2. Thẻ bài rơi từ tán tu — 100%, đúng 1 cái mỗi con (xem mục CÂN BẰNG ở trên).
--
--    Gắn vào `drop_table` có sẵn của loài, tra theo `code` chứ không hardcode id (tiền lệ V28/V30).
-- ---------------------------------------------------------------------------------------------
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 1, 1
  FROM drop_table dt, item_template it
 WHERE dt.code = 'wandering_bandit'
   AND it.code = 'outer_sect_tally'
   AND NOT EXISTS (
       SELECT 1 FROM drop_table_entry e
        WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

-- ---------------------------------------------------------------------------------------------
-- 3. Quest 6. Nội dung (mục tiêu, phần thưởng) nằm HẾT trong script (Điều 27); bảng này chỉ giữ
--    định danh + đường dẫn script + điều kiện MỞ.
--
--    `prerequisite_quest_id` trỏ về `report_to_outer_sect` (ADR-044, mốc CLAIMED): không ai giao
--    việc ngoại môn cho một người chưa báo danh ngoại môn.
-- ---------------------------------------------------------------------------------------------
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'recover_outer_sect_tallies',
       'MAIN',
       '/scripts/quest/recover_outer_sect_tallies.js',
       (SELECT id FROM quest_template WHERE code = 'report_to_outer_sect')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'recover_outer_sect_tallies');
