// rank_board_dispute.js — nội dung quest "rank_board_dispute" (Điều 27, GAMEPLAY.md §9.6).
//
// STORY: arc-2 §6 — nhịp XUNG ĐỘT. Nguyên văn §6: *"Bạch Kính Chi và vây cánh chèn ép 'đứa tạp dịch
// đi cửa sau'; tài nguyên tu luyện ngoại môn phân phát theo thứ hạng — người chơi phải cày từ đáy
// bảng"*. Đây là chỗ mạch đó lần đầu thành việc làm được.
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2): Bạch Kính Chi được NHẮC TỚI, không xuất hiện. §5 khai ông ta "bị
// vượt ở arc 3" — dựng ông thành NPC đánh nhau ở đây là tiêu trước một nhịp arc 3 không còn chỗ trả.
// Người chơi chỉ thấy hậu quả: phần tài nguyên của mình bị cắt, và phải tự ra rìa Hoang Vực kiếm.
//
// =============================================================================================
// HAI BƯỚC CÓ THỨ TỰ
// =============================================================================================
//   1. `monster_killed` wasteland_beast × 2 — tự đi kiếm phần của mình ở rìa Hoang Vực
//   2. `npc_talked` senior_sister           — Tô Vân Nhi là người nói cho biết chuyện bảng thứ hạng
//
// Bước 2 mở lời với TÔ VÂN NHI chứ không phải người giao quest — bài học quest 7.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "wasteland_beast") {
            progress.felled = progress.felled + 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "senior_sister"
                && progress.felled >= 2
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(4600);
    }
})
