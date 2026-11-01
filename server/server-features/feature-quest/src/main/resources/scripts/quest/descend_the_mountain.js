// descend_the_mountain.js — quest 26.
// STORY: arc-4 §6 — mở arc: lệnh tông "Kim Đan phải luyện tâm trong hồng trần" — xuống núi lịch luyện
// Hoang Vực sâu. Đối thủ: tà tu Hoang Vực @312, đánh bằng bộ Nội Môn (biên thật +3); thưởng là bộ "Kim Đan" — điều
// kiện cần của mọi trận phía sau (quật mạch đồ chặn bộ Nội Môn ở biên +1).
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2/3).
// Nhịp hé lộ (§8 quy tắc 2): arc 4 được nói yêu tộc là NẠN NHÂN của Đại Kiếp và Huyết Sa đang ĐÀO linh mạch chết "tìm
// thứ gì đó" — KHÔNG nói đó là vết nứt Thiên Môn (arc 5 mới lộ), KHÔNG danh tính nội gián, KHÔNG Tô Vân Nhi phản bội.
// Số liệu cân bằng: V113 + GoldenCoreBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-139).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ wasteland_marauder: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "wasteland_marauder" && progress.wasteland_marauder < 1) {
            progress.wasteland_marauder += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.wasteland_marauder >= 1
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(18000);
        ctx.give_item("golden_core_blade", 1);
        ctx.give_item("golden_core_robe", 1);
        ctx.give_item("golden_core_cap", 1);
        ctx.give_item("golden_core_gloves", 1);
        ctx.give_item("golden_core_boots", 1);
        ctx.give_item("golden_core_talisman", 1);
    }
})
