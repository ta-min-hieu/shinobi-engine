-- V58 — năm trận bắt buộc vi phạm luật biên thật (+2, chủ dự án chốt 2026-10-01) nay ĐẠT luật bằng
-- cách hạ ATK quái — chủ dự án duyệt bảng 2026-10-17 (lô 14, docs/README.md). CHỈ ĐỔI CỘT `atk`,
-- không đổi máu/giáp/vị trí của loài nào.
--
-- CÁCH CHỌN SỐ: mỗi loài, giữ nguyên máu và giáp, lấy ATK CAO NHẤT còn cho biên thật ≥ +2 (dò bằng máy
-- trên đúng mô hình `DuelMargin` của các test cân bằng, mô hình khớp số của cả sáu test 🔴 trước đó).
-- Sau máu theo cấp (+2/cấp, CombatConstants.characterMaxHp, lô 13):
--
--   loài                  | trận            | biên thật trước | atk      | biên thật sau
--   archive_warden        | quest 3, Lv5    |  0              | 26 → 19  | +2
--   trial_construct       | quest 4, Lv8    |  0              | 15 → 12  | +2
--   vein_raider           | quest 10, Lv10  | +1              | 18 → 15  | +2
--   raider_chieftain      | quest 12, Lv15  |  0              | 30 → 26  | +2
--   blood_sand_acolyte    | quest 13, Lv22  | +1              | 34 → 31  | +2 (31 chứ không 32: đàn chủ phải đánh đau hơn)
--   blood_sand_ringleader | quest 13, Lv22  | +1              | 35 → 32  | +2
--
-- NHỮNG GÌ VẪN GIỮ (đã soi bằng máy trước khi chọn):
--   * cổng trang bị, đo bằng luật biên thật (thứ trọng tài từ 2026-10-01), KHÔNG còn bằng "thua trên giấy":
--     tay không vs cỗ khảo hạch biên thật +1 (giấy +4) — vẫn dưới +2; tay không vs thạch vệ biên thật −1
--     (giấy +2); bộ Tạp Dịch vs đầu lĩnh đoạt mạch biên thật −2 NGAY CẢ ở máu đầy (giấy +1) — phần thưởng
--     quest 11 vẫn là điều kiện cần. ⚠️ Bản đề xuất gửi chủ dự án ghi "−3" cho trận này: mô hình đường đi
--     lúc dò khác của test; số đúng là −2, kết luận không đổi.
--   * bậc thang: đoạt mạch (15) > cỗ khảo hạch (12); đầu lĩnh (26) > đoạt mạch (15); tế đồ (31) > đầu
--     lĩnh (26); đàn chủ (32) > tế đồ (31); yêu thú Hoang Vực (36, không đổi) > đàn chủ (32);
--     cỗ khảo hạch (12) vẫn đánh nhẹ hơn thạch vệ (19).
--   * ⚠️ thạch vệ (19) nay đánh NHẸ hơn tán tu (22) — không có bộ chỉ số nào đạt biên mà vẫn đánh đau
--     hơn (đã quét). Chủ dự án chọn hướng này: nó vẫn DAI hơn hẳn (120 máu / 14 giáp so với 90 / 10).
UPDATE monster_template SET atk = 19 WHERE code = 'archive_warden'        AND atk = 26;
UPDATE monster_template SET atk = 12 WHERE code = 'trial_construct'       AND atk = 15;
UPDATE monster_template SET atk = 15 WHERE code = 'vein_raider'           AND atk = 18;
UPDATE monster_template SET atk = 26 WHERE code = 'raider_chieftain'      AND atk = 30;
UPDATE monster_template SET atk = 31 WHERE code = 'blood_sand_acolyte'    AND atk = 34;
UPDATE monster_template SET atk = 32 WHERE code = 'blood_sand_ringleader' AND atk = 35;
