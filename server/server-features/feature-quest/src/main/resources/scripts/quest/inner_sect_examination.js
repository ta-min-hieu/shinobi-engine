// inner_sect_examination.js — quest 21 (Điều 27, GAMEPLAY.md §9.6).
// STORY: arc-3 §6 — mở arc: "qua khảo hạch nội môn". Đối thủ: Trắc Nội Môn Thạch Nhân @232, đánh bằng bộ Trúc Cơ
// (biên thật +3); thưởng là bộ "Nội Môn" — điều kiện cần của mọi trận phía sau (infiltrator chặn bộ Trúc Cơ ở biên 0).
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2).
// Nhịp hé lộ (§8 quy tắc 2): arc 3 chỉ được NGHI — chữ "Thiên Môn" lần đầu nhắc ở quest 2 rồi bị lảng;
// nội gián cấp trưởng lão chỉ là SUY ĐOÁN, danh tính giữ tới arc 5; KHÔNG chạm Tô Vân Nhi phản bội (arc 5).
// Số liệu cân bằng: V109 + InnerSectBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-130).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ inner_trial_construct: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "inner_trial_construct" && progress.inner_trial_construct < 1) {
            progress.inner_trial_construct += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.inner_trial_construct >= 1
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(9000);
        ctx.give_item("inner_sect_blade", 1);
        ctx.give_item("inner_sect_robe", 1);
        ctx.give_item("inner_sect_cap", 1);
        ctx.give_item("inner_sect_gloves", 1);
        ctx.give_item("inner_sect_boots", 1);
        ctx.give_item("inner_sect_talisman", 1);
    }
})
