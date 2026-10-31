// forbidden_archive_glow.js — quest 22.
// STORY: arc-3 §6 — twist kép (1): mảnh ngọc cổ phát sáng ở Tàng Thư Đường tầng cấm. Đệ tử nội môn được phép xuống
// tầng cấm; lối xuống vẫn do thạch vệ Tàng Thư canh (archive_warden — chúng đánh cả người của tông, STORY §7). Báo
// lại Chu Hoành: ông biến sắc, lần đầu buột ra hai chữ "Thiên Môn" rồi lảng đi.
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2).
// Nhịp hé lộ (§8 quy tắc 2): arc 3 chỉ được NGHI — chữ "Thiên Môn" lần đầu nhắc ở quest 2 rồi bị lảng;
// nội gián cấp trưởng lão chỉ là SUY ĐOÁN, danh tính giữ tới arc 5; KHÔNG chạm Tô Vân Nhi phản bội (arc 5).
// Số liệu cân bằng: V109 + InnerSectBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-130).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ archive_warden: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "archive_warden" && progress.archive_warden < 2) {
            progress.archive_warden += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.archive_warden >= 2
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
    }
})
