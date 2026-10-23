-- Dòng 28 (lô 18, chủ dự án duyệt 2026-10-23): một nhân vật chỉ có MỘT đơn xin vào bang đang chờ.
-- Trước đây luật này chỉ được kiểm ở application (SendGuildJoinRequestUseCase hỏi "đã có đơn chờ chưa"
-- rồi mới ghi) ⇒ hai lệnh xin vào HAI bang khác nhau gửi cùng lúc đều lọt. UNIQUE(guild_id, character_id)
-- của V14 chỉ chặn trùng trong cùng một bang.
--
-- Dọn trước: giữ đơn CŨ NHẤT của mỗi nhân vật (đơn gửi trước được giữ, như luật application vốn làm), xoá
-- phần thừa — đơn chờ không mang tài sản gì, mất đơn thừa chỉ là người chơi phải gửi lại.
DELETE FROM guild_join_request gjr
 USING guild_join_request older
 WHERE older.character_id = gjr.character_id
   AND (older.requested_at, older.id) < (gjr.requested_at, gjr.id);

ALTER TABLE guild_join_request
    ADD CONSTRAINT uq_guild_join_request_character UNIQUE (character_id);
