// recover_outer_sect_tallies.js — nội dung quest "recover_outer_sect_tallies" (Điều 27,
// GAMEPLAY.md §9.5), KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java).
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp VIỆC VẶT đầu tiên của arc, và GIEO MẦM cho twist "đệ tử mất
// tích". Khai theo STORY.md §8 quy tắc 1. Nhịp hé lộ (§8 quy tắc 2): ở đây lời giải thích chính
// thức vẫn là "đứa đó bỏ trốn" — KHÔNG một chữ nào về hang luyện công, hút tu vi, hay Huyết Sa.
//
// =============================================================================================
// QUEST ĐẦU TIÊN CỦA DỰ ÁN DÙNG MỤC TIÊU "MANG VẬT X TỚI NPC Y"
// =============================================================================================
// Nó đi trên chính sự kiện `npc_talked` của quest 5, cộng MỘT hiệu ứng mới: `ctx.take_item`.
// Ba điều bắt buộc của kiểu mục tiêu này, mỗi điều đều có test gác (GAMEPLAY.md §9.5):
//
//   1. `event.type` PHẢI kiểm trước. Mỗi quest đang làm nhận MỌI loại sự kiện, nên không có nó thì
//      `event.npc_code` là `undefined` trong sự kiện quái chết và mọi so sánh phía sau là ngẫu nhiên.
//   2. Tiến độ là CỜ (`delivered`), KHÔNG phải bộ đếm — mốc đặt ở lúc MỞ LỜI nên sự kiện LẶP LẠI.
//   3. Điều kiện phải có `!progress.delivered`. Thiếu nó thì mỗi lần chào hỏi lại là một lần đòi đồ,
//      và người chơi mất thêm 2 thẻ bài cho một quest đã xong.
//
// `take_item` KHÔNG lấy đồ ngay: nó là một ĐIỀU KIỆN gắn vào cả lần chạy hook. Java ghi tiến độ và
// lấy đồ trong CÙNG một transaction — chưa đủ 2 thẻ bài thì lần mở lời này không để lại dấu vết gì,
// cờ `delivered` ở dưới cũng không bao giờ được ghi xuống. Vì vậy script KHÔNG cần (và không được)
// tự kiểm túi: không có cách nào để nó kiểm mà không mở ra một cửa sổ đua.
//
// =============================================================================================
// 1.000 EXP — chọn từ tương quan, không chọn rồi mới đi kiểm
// =============================================================================================
// Cùng loài với quest 2 (tán tu), nhưng hai con thay vì một, và điểm xa nhất là x=34 thay vì x=21.
// Quest 2 = 800; quest 3 (2 thạch vệ, dai gấp rưỡi) = 1.200; quest này nằm giữa, nghiêng về quest 2
// vì người chơi nay đã Cấp 10 và có trang bị Tạp Dịch. Đường chơi tối thiểu:
// 4.585 (hết arc 1) + 200 (quest 5) + 2 × 35 (tán tu) + 1.000 = 5.855 ⇒ đóng quest ở **Cấp 11**
// (`ExperienceCurve` tuyến tính: Cấp 11 = 5.500, Cấp 12 = 6.600).
//
// KHÔNG thưởng thêm vật phẩm. Quest 5 vừa cho 2 viên đan; một phần thưởng vật phẩm nữa ngay sau đó
// làm hỏng chỗ đứng của đan dược (món DÙNG ĐƯỢC duy nhất, nguồn hồi máu mua bằng vàng — ADR-046).
// =============================================================================================
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ delivered: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "npc_talked" && event.npc_code === "senior_sister" && !progress.delivered) {
            progress.delivered = true;
            ctx.take_item("outer_sect_tally", 2);
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.delivered) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(1000);
    }
})
