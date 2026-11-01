// quiet_the_frenzied_kin.js — quest 29.
// STORY: arc-4 §6 — đánh nhau trước, hiểu nhau sau: yêu tộc nhờ người chơi giải thoát đồng loại đã biến
// dị cuồng loạn quanh mạch chết (frenzied_vein_beast @376/392) — bọn Huyết Sa đào tới đâu, mạch chết lan tới đó.
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2/3).
// Nhịp hé lộ (§8 quy tắc 2): arc 4 được nói yêu tộc là NẠN NHÂN của Đại Kiếp và Huyết Sa đang ĐÀO linh mạch chết "tìm
// thứ gì đó" — KHÔNG nói đó là vết nứt Thiên Môn (arc 5 mới lộ), KHÔNG danh tính nội gián, KHÔNG Tô Vân Nhi phản bội.
// Số liệu cân bằng: V113 + GoldenCoreBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-139).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ frenzied_vein_beast: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "frenzied_vein_beast" && progress.frenzied_vein_beast < 2) {
            progress.frenzied_vein_beast += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.frenzied_vein_beast >= 2
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(24000);
    }
})
