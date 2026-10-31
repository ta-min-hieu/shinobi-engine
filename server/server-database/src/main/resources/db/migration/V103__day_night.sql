-- V103 — ngày đêm (ADR-123, lô 23). Điểm spawn có thể chỉ ra quái ban đêm (ban ngày chờ, không hồi sinh; quái đã ra
-- lúc đêm không tự biến mất khi trời sáng). Bản đầu đánh dấu con THỨ HAI của hai cặp quái trùng loài — ban ngày mỗi loài
-- vẫn còn một con nên không chặn nhiệm vụ: Huyết Sa hộ pháp ở x=136 ("Huyết Sa hoạt động về đêm") và yêu thú Hoang Vực ở
-- x=200 ("yêu thú đi săn đêm"). Là ĐỀ XUẤT nội dung, chủ dự án duyệt lại.
ALTER TABLE monster_spawn ADD COLUMN night_only BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE monster_spawn ms
   SET night_only = TRUE
  FROM monster_template mt
 WHERE mt.id = ms.monster_template_id
   AND ms.world_code = 'main'
   AND ((mt.code = 'blood_sand_acolyte' AND ms.pos_x = 136) OR (mt.code = 'wasteland_beast' AND ms.pos_x = 200));
