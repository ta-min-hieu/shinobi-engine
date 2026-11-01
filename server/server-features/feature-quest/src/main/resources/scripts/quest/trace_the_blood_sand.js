// trace_the_blood_sand.js — quest 27.
// STORY: arc-4 §6 — truy dấu Huyết Sa: quật mạch đồ Huyết Sa (blood_sand_digger @328/344) đào bới
// quanh một linh mạch đã chết — chúng tìm thứ gì đó dưới đất, chưa ai biết là gì.
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2/3).
// Nhịp hé lộ (§8 quy tắc 2): arc 4 được nói yêu tộc là NẠN NHÂN của Đại Kiếp và Huyết Sa đang ĐÀO linh mạch chết "tìm
// thứ gì đó" — KHÔNG nói đó là vết nứt Thiên Môn (arc 5 mới lộ), KHÔNG danh tính nội gián, KHÔNG Tô Vân Nhi phản bội.
// Số liệu cân bằng: V113 + GoldenCoreBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-139).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ blood_sand_digger: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "blood_sand_digger" && progress.blood_sand_digger < 2) {
            progress.blood_sand_digger += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.blood_sand_digger >= 2
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(20000);
    }
})
