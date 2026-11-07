-- ADR-205 — xoá nhân vật có chờ: đánh dấu lúc yêu cầu xoá; trong 3 ngày (CharacterDeletionFacade.GRACE) vào lại nhân vật
-- là khôi phục; quá hạn thì nhân vật biến khỏi danh sách và không còn chiếm chỗ trong trần 4 nhân vật / tài khoản. Dòng KHÔNG
-- bị xoá thật (khoá ngoại khắp nơi) và tên vẫn giữ.
ALTER TABLE character ADD COLUMN deletion_requested_at TIMESTAMPTZ;
