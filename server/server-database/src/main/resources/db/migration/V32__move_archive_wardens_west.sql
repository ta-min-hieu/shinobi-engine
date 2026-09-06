-- V32 — dời hai điểm spawn `archive_warden` sang PHÍA TÂY (ROADMAP mục 62c + 63).
--
-- CHỈ ĐỔI DỮ LIỆU. Không cột mới, không bảng mới, không ràng buộc mới.
--
-- ⚠️ Số hiệu nhảy từ V30 sang V32 CÓ CHỦ Ý: V31 được dành cho một đợt art chạy song song cùng ngày.
-- Flyway chạy theo thứ tự phiên bản nên khoảng trống không gây hại.
--
-- ============================================================================================
-- VÌ SAO PHẢI DỜI — số đo, không phải cảm giác
-- ============================================================================================
-- Playtest 2026-09-06 bằng `gameProbe` (đo HAI lần, kết quả trùng khít): nhân vật cấp 6 mặc trọn
-- bộ "Tạp Dịch" đi bộ từ làng (x=0) ra chỗ thạch vệ cũ (x=60) mất **65 máu dọc đường** —
-- tán tu@21 đánh 17, tán tu@34 đánh 17, quỷ đá@47 đánh 31 — và **KHÔNG có hồi máu ngoài chiến
-- đấu** trong toàn bộ server-world. Tới nơi còn 53/118, mà thạch vệ đánh 20/nhát ⇒ chết sau 3
-- nhát trong khi cần 4 nhát mới hạ được nó. Phép tính tay đôi ở V30 KHÔNG sai (32 vs 20, 4 nhát
-- vs 6 nhát, thắng ở máu đầy) — cái sai là con đường tới đó.
--
-- Và một lỗ hình học mà luật V29 không bắt được: V29 giãn spawn theo **tầm aggro** (6+6=12 < 13m),
-- nhưng khi người chơi ĐỨNG LẠI ĐÁNH thì thứ quyết định là **tầm đuổi (leash) = 10m**. Quỷ đá ở
-- x=47 đuổi tới x=57, cách chỗ đánh nhau (x=60) đúng 3m — trong tầm đánh 2m cộng đà di chuyển.
-- Lần đo thứ hai, chính quỷ đá là con ra đòn kết liễu.
--
-- ============================================================================================
-- VÌ SAO PHÍA TÂY, chứ không phải "xa hơn về phía đông"
-- ============================================================================================
-- 1. **Tường thuật đã nói vậy từ đầu.** Lời thoại của Chu Hoành (`town_elder.js`, V30): *"Góc phía
--    TÂY Tàng Thư Đường bỏ không đã lâu"*. V30 lại đặt spawn ở x=60/73, tức phía ĐÔNG, sau lưng cả
--    hai tán tu lẫn quỷ đá. Bản này làm bản đồ khớp lại với lời thoại.
-- 2. **Phía tây trống thật.** Trên đường x < 0 chỉ có trưởng lão Chu Hoành ở x=-5 (NPC, không đánh
--    ai). Không một điểm spawn quái nào — nên đường đi sạch và người chơi tới nơi với 118/118.
-- 3. Dời ra xa hơn về phía đông (65/78) chỉ vá được lỗ leash mà KHÔNG chữa 65 máu đường đi —
--    người chơi vẫn tới nơi với ~53 máu và vẫn thua. Chủ dự án chọn phương án này sau khi xem số.
--
-- Bố cục mới trên trục chơi:
--
--     thạch vệ -33 · thạch vệ -20 · [Chu Hoành -5] · [người chơi 0] · mộc nhân 8 · mộc nhân 14
--                                 · tán tu 21 · tán tu 34 · quỷ đá 47      (ba số cuối là của V29)
--
-- Kiểm lại LUẬT V29 cho mọi cặp có aggro (tổng aggro của mọi cặp = 12m):
--   |-20 - (-33)| = 13 > 12 ✓   |-20 - 21| = 41 ✓   |-33 - 21| = 54 ✓   (các cặp cũ giữ nguyên)
-- Kiểm LUẬT MỚI "chỗ đánh nhau yên tĩnh" (|A-B| > leash của A + tầm đánh 2m = 12m):
--   điểm gần nhất về phía đông là tán tu@21 ⇒ |-20 - 21| = 41 > 12 ✓
-- ============================================================================================
UPDATE monster_spawn
   SET pos_x = -20
 WHERE world_code = 'main'
   AND pos_x = 60
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'archive_warden');

UPDATE monster_spawn
   SET pos_x = -33
 WHERE world_code = 'main'
   AND pos_x = 73
   AND monster_template_id = (SELECT id FROM monster_template WHERE code = 'archive_warden');
