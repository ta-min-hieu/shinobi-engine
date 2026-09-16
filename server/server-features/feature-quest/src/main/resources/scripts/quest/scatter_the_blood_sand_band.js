// scatter_the_blood_sand_band.js — nội dung quest "scatter_the_blood_sand_band" (Điều 27,
// GAMEPLAY.md §9.6), KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* — hook KHÔNG trả giá trị.
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp ĐÓNG của quãng Lv22–27, khai theo STORY.md §8 quy tắc 1.
//
// =============================================================================================
// ĐÓNG MỘT TOÁN, KHÔNG ĐÓNG MỘT GIÁO PHÁI
// =============================================================================================
// §7 (dòng thêm cùng PR này) viết rõ đàn chủ là gì: *"Không phải hộ pháp — chỉ là đàn chủ một toán
// nhỏ, nên hạ được hắn thì toán tan chứ giáo không suy suyển gì"*. Đó là cái giới hạn CỐ Ý của
// quest này.
//
// Cao trào arc 2 đã tiêu mất một hộ pháp rồi (quest 9). Hứa thêm một hộ pháp nữa ở đây là hứa một
// khoản mà arc 2 không còn chỗ để trả — §8 quy tắc 2. Nên Chu Hoành đóng lại đúng mức ông biết:
// đường đông sạch, người của tông đi lấy khoáng được, hết. Không ai nói "đã xong với Huyết Sa".
//
// ⚠️ NHỊP HÉ LỘ — KHÔNG được chạm: vết nứt Thiên Môn (arc 3+), Sa Y Nhân (arc 6), nội gián trong
// tông (arc 3 twist), và Tô Vân Nhi (arc 5).
//
// =============================================================================================
// HAI BƯỚC CÓ THỨ TỰ
// =============================================================================================
//   1. `monster_killed` blood_sand_ringleader — hạ đàn chủ ở x=148
//   2. `npc_talked` town_elder                — về báo, và quãng Lv22–27 đóng
//
// ⚠️ Quest này NHẬN TỪ CHU HOÀNH, và bước 2 lại là mở lời với CHÍNH ÔNG — đúng cái hình dạng mà
// quest 7 đã trả giá. Ở đây nó AN TOÀN vì bước 1 đứng trước: `progress.felled` gác bước 2, nên sự
// kiện `npc_talked` bắn ra lúc nhận quest (khi `felled` còn false) không đóng được gì. Cùng khuôn
// `break_the_raider_band` (quest 12) đã dùng để đóng arc 1.
//
// =============================================================================================
// 4.400 EXP — VÀ ĐÂY LÀ CHỖ QUÃNG NÀY ĐÓNG Ở ĐÚNG CẤP 27
// =============================================================================================
// Đường chơi tối thiểu của lát cắt: 3.600 + 4.000 + 4.400 = **12.000**, đúng bằng tổng
// `ExperienceCurve` cần để đi Cấp 22 → 27 (tuyến tính 100 × cấp: 2.200 + 2.300 + 2.400 + 2.500 +
// 2.600). Cộng EXP rơi từ quái dọc đường thì người chơi đóng quãng ở Cấp 27 có dư — cùng cách V45
// tính cho arc 1 khép ở đúng Cấp 18.
//
// Đàn chủ đứng ở **x = 148**, xa nhất trục chơi từ trước tới nay (sào huyệt quest 12 ở 93).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: false, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        // Bước 1 — bước mở, không có điều kiện đứng trước.
        if (event.type === "monster_killed" && event.monster_code === "blood_sand_ringleader") {
            progress.felled = true;
        }

        // Bước 2 — chỉ tính SAU khi đã hạ hắn. Đây là thứ làm cho việc "người giao quest cũng là
        // người nhận báo cáo" không lặp lại lỗi quest 7.
        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.felled
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(4400);
    }
})
