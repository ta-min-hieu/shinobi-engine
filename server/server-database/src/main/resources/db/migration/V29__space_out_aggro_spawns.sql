-- V29 — giãn các điểm spawn CÓ AGGRO ra đủ xa để không con nào bị kéo kèm con khác.
--
-- Vấn đề (đo 2026-09-05, ROADMAP mục 57): `wandering_bandit` spawn ở x=21 và x=27 — đúng bằng
-- `aggro_radius_meters = 6` của chính loài đó. Một điểm p kéo được CẢ HAI con A, B khi
-- |p-A| <= 6 và |p-B| <= 6, tức khi |A-B| <= 12. Với 6m thì MỌI vị trí trong đoạn [21, 27] đều
-- kéo cả hai, và sát thương nhận vào GẤP ĐÔI (34/giây thay vì 17). Người chơi đi làm quest
-- `night_watch_bandit` bị đẩy vào đúng đoạn đó.
--
-- ⚠️ Không phải lỗi CHỈ SỐ. Phép tính tay đôi (NightWatchDuelBalanceTest) cho thấy cấp 4 mặc trọn
-- bộ Tạp Dịch thắng 3 nhát so với 7, tay không vẫn thắng 4 so với 6 — con tán tu vốn đã cân. Hạ
-- chỉ số của nó sẽ chữa triệu chứng bằng cách phá một trận đấu đang đúng.
--
-- ⚠️ `stone_ogre` (x=34) cũng chỉ cách con tán tu thứ hai 7m ⇒ CŨNG chồng aggro. Giãn mỗi cặp tán
-- tu mà bỏ qua nó chỉ là dời lỗi sang chỗ khác, nên V29 xếp lại cả cụm.
--
-- Luật mới: hai điểm spawn có aggro phải cách nhau > 2 × aggro_radius (> 12m với aggro 6).
--   tán tu #1   21  (giữ nguyên — quest arc 1 dẫn người chơi tới đây)
--   tán tu #2   21 + 13 = 34
--   thạch yêu   34 + 13 = 47
-- Mộc nhân (`aggro_radius_meters = 0`) không tham gia luật này: chúng không bao giờ tự đánh ai.
--
-- Định danh dòng theo (mã loài, toạ độ CŨ), KHÔNG theo id — id do `GENERATED ALWAYS AS IDENTITY`
-- cấp, cùng lý do V28 tra `quest_template` theo `code`.

UPDATE monster_spawn
   SET pos_x = 34
 WHERE pos_x = 27
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'wandering_bandit');

UPDATE monster_spawn
   SET pos_x = 47
 WHERE pos_x = 34
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'stone_ogre');
