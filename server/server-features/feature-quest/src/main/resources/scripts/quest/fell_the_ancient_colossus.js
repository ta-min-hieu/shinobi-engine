// fell_the_ancient_colossus.js — quest 30.
// STORY: arc-4 §6 — cao trào: Huyết Sa đánh thức một cổ thạch quái trấn giữ linh mạch chết (máy móc
// hộ mạch của Linh Triều mất kiểm soát — dòng stone_ogre nâng cấp). Người chơi + Bạch Kính Chi + yêu tộc liên thủ hạ nó
// (ancient_stone_colossus @408; hai đồng minh có mặt trong lời thoại, trận là tay đôi theo luật biên).
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2/3).
// Nhịp hé lộ (§8 quy tắc 2): arc 4 được nói yêu tộc là NẠN NHÂN của Đại Kiếp và Huyết Sa đang ĐÀO linh mạch chết "tìm
// thứ gì đó" — KHÔNG nói đó là vết nứt Thiên Môn (arc 5 mới lộ), KHÔNG danh tính nội gián, KHÔNG Tô Vân Nhi phản bội.
// Số liệu cân bằng: V113 + GoldenCoreBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-139).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ ancient_stone_colossus: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "ancient_stone_colossus" && progress.ancient_stone_colossus < 1) {
            progress.ancient_stone_colossus += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.ancient_stone_colossus >= 1
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(30000);
    }
})
