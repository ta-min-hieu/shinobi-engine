-- V54 — tên nhân vật và tên bang không được trùng nhau khi bỏ qua hoa-thường (chủ dự án chọn 2026-10-14,
-- docs/README.md dòng 42). Đổi schema: chỉ THÊM hai index, không đổi cột nào.
--
-- VÌ SAO: UNIQUE sẵn có của V2 (`character.name`) và V14 (`guild.name`) so theo byte, nên `Kage` và
-- `kage` là hai nhân vật khác nhau — đường mạo danh rẻ nhất. Tên MỚI đã được chuẩn hoá NFC ở tầng
-- ứng dụng (cùng ngày), nên so `lower(name)` ở đây là so đúng thứ người chơi nhìn thấy.
--
-- Tra tên (thách đấu, giao dịch, xin vào bang) VẪN phân biệt hoa-thường — chỉ việc TẠO bị chặn.
-- Hai index giữ nguyên UNIQUE cũ (không xoá): adapter dịch MỌI vi phạm unique lúc INSERT thành "tên
-- trùng" (`insertReturningIdOrThrow`), nên cả hai đường đều ra cùng một câu báo.
--
-- ⚠️ Nếu DB đã có sẵn hai tên chỉ khác hoa-thường thì migration này ĐỎ — cố ý: phải đổi tên một bên
-- bằng tay rồi mới chạy (DB dev 2026-10-14 đã kiểm: 0 cặp).
CREATE UNIQUE INDEX character_name_lower_key ON character (lower(name));

CREATE UNIQUE INDEX guild_name_lower_key ON guild (lower(name));
