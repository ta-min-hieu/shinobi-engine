// leaked_examination.js — quest 23.
// STORY: arc-3 §6 — twist kép (2): tin khảo hạch bị lộ cho Huyết Sa. Tế đồ trà trộn (blood_sand_infiltrator, @248/264)
// mang theo lịch khảo hạch mà chỉ hàng trưởng lão được biết ⇒ Chu Hoành suy ra trong tông có nội gián CẤP TRƯỞNG LÃO.
// Ông KHÔNG đoán là ai (danh tính giữ tới arc 5).
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2).
// Nhịp hé lộ (§8 quy tắc 2): arc 3 chỉ được NGHI — chữ "Thiên Môn" lần đầu nhắc ở quest 2 rồi bị lảng;
// nội gián cấp trưởng lão chỉ là SUY ĐOÁN, danh tính giữ tới arc 5; KHÔNG chạm Tô Vân Nhi phản bội (arc 5).
// Số liệu cân bằng: V109 + InnerSectBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-130).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ blood_sand_infiltrator: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "blood_sand_infiltrator" && progress.blood_sand_infiltrator < 3) {
            progress.blood_sand_infiltrator += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.blood_sand_infiltrator >= 3
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(11000);
    }
})
