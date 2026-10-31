// repel_the_altar_master.js — quest 25, nhịp ĐÓNG arc 3.
// STORY: arc-3 §6 — cao trào: Huyết Sa tập kích lễ đại bỉ võ để bắt các hạt giống Tử Phủ. Toán tế đồ đã tan ở quest
// 23; ở đây đẩy lui đàn chủ Huyết Sa (blood_sand_altar_master @296, biên thật +2 với bộ Nội Môn bằng đòn
// thường; Thần Thông mở ở Lv50 là lợi thế thêm, không phải điều kiện). Báo Chu Hoành ⇒ danh phận nội môn.
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2).
// Nhịp hé lộ (§8 quy tắc 2): arc 3 chỉ được NGHI — chữ "Thiên Môn" lần đầu nhắc ở quest 2 rồi bị lảng;
// nội gián cấp trưởng lão chỉ là SUY ĐOÁN, danh tính giữ tới arc 5; KHÔNG chạm Tô Vân Nhi phản bội (arc 5).
// Số liệu cân bằng: V109 + InnerSectBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-130).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ blood_sand_altar_master: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "blood_sand_altar_master" && progress.blood_sand_altar_master < 1) {
            progress.blood_sand_altar_master += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.blood_sand_altar_master >= 1
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(16000);
    }
})
