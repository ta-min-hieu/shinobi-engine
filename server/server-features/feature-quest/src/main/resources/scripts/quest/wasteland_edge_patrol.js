// wasteland_edge_patrol.js — nội dung quest "wasteland_edge_patrol" (Điều 27, GAMEPLAY.md §9.6).
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp MỞ của quãng Lv28–36, quãng ĐÓNG arc 2.
//
// =============================================================================================
// QUEST NÀY TRAO BỘ "TRÚC CƠ", VÀ ĐÓ LÀ MỘT RÀNG BUỘC SỐ HỌC, KHÔNG PHẢI NHỊP KỂ CHUYỆN
// =============================================================================================
// Hai loài của V48 (`wasteland_beast` 280/44/32, `foundation_warden` 300/46/35) được cân TRÊN
// người chơi ĐÃ mặc bộ Trúc Cơ (công 82 · thủ 65 · máu 206). Với bộ Ngoại Môn cũ (64/50/170):
//
//     yêu thú: người chơi ceil(280/48) = 6 nhát · quái ceil(141/29) = 5 nhát  ⇒ biên −1, THUA
//
// Và không có bộ số nào cho biên +2 với bộ cũ mà không trùng đúng chỉ số `blood_sand_acolyte`
// (220/34/26) hoặc `blood_sand_ringleader` (240/35/28) — tức KHÔNG CÒN bậc thang nào ở trên bộ
// Ngoại Môn. Đó là trần đã báo cáo 2026-09-16, và nó quyết định thứ tự chuỗi này: đồ phải tới
// TRƯỚC con quái đầu tiên của quãng.
//
// Vì thế trận của chính quest này đánh loài ĐÃ CÓ (`blood_sand_ringleader` @148) — thứ bộ Ngoại
// Môn thắng được với biên +2 đã đo thật ở V47.
//
// =============================================================================================
// HAI BƯỚC CÓ THỨ TỰ
// =============================================================================================
//   1. `monster_killed` blood_sand_ringleader — dọn lại đường đông trước khi đi xa hơn
//   2. `npc_talked` town_elder                — về nhận đồ
//
// ⚠️ Nhận từ Chu Hoành và báo lại cho CHÍNH ông: an toàn vì `progress.cleared` gác bước 2, đúng
// khuôn quest 12 và 15 đã dùng. Sự kiện `npc_talked` bắn lúc nhận quest không đóng được gì.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ cleared: false, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "blood_sand_ringleader") {
            progress.cleared = true;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.cleared
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
        // Bộ "Trúc Cơ" — SÁU món, ô ACCESSORY_2 để trống, đúng lũt đồ mà mọi lớp cân bằng giả định.
        ctx.give_item("foundation_blade", 1);
        ctx.give_item("foundation_robe", 1);
        ctx.give_item("foundation_cap", 1);
        ctx.give_item("foundation_gloves", 1);
        ctx.give_item("foundation_boots", 1);
        ctx.give_item("foundation_talisman", 1);
    }
})
