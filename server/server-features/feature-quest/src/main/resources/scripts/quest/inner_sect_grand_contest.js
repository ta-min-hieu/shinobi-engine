// inner_sect_grand_contest.js — quest 24.
// STORY: arc-3 §6 — đại bỉ võ toàn tông: người chơi VƯỢT Bạch Kính Chi (§5 — mốc "phàm nhân vượt thiên tài" đầu
// tiên). Trận là PvE scripted (bach_kinh_chi @280, biên thật +2 với bộ Nội Môn). Báo lại sư tỷ Tô Vân Nhi — người
// dắt người chơi từ thuở ngoại môn; ở arc 3 cô CHỈ mừng, không có gì khác.
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2).
// Nhịp hé lộ (§8 quy tắc 2): arc 3 chỉ được NGHI — chữ "Thiên Môn" lần đầu nhắc ở quest 2 rồi bị lảng;
// nội gián cấp trưởng lão chỉ là SUY ĐOÁN, danh tính giữ tới arc 5; KHÔNG chạm Tô Vân Nhi phản bội (arc 5).
// Số liệu cân bằng: V109 + InnerSectBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-130).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ bach_kinh_chi: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "bach_kinh_chi" && progress.bach_kinh_chi < 1) {
            progress.bach_kinh_chi += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "senior_sister"
                && progress.bach_kinh_chi >= 1
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(13000);
    }
})
