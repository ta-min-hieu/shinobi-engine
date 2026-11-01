// free_the_awakened_beast.js — quest 28.
// STORY: arc-4 §6 — TWIST: cứu một yêu tộc khai trí khỏi tay Huyết Sa. Cai ngục Huyết Sa
// (blood_sand_beast_warden @360) giữ nó làm "chó dò mạch". Được thả, nó kể: yêu tộc cũng là nạn nhân của Đại Kiếp —
// linh mạch chết trước tiên ở đất của chúng.
//
// Bước có thứ tự: hạ đủ quái TRƯỚC, rồi mới báo NPC (báo sớm không tính — cùng khuôn arc 2/3).
// Nhịp hé lộ (§8 quy tắc 2): arc 4 được nói yêu tộc là NẠN NHÂN của Đại Kiếp và Huyết Sa đang ĐÀO linh mạch chết "tìm
// thứ gì đó" — KHÔNG nói đó là vết nứt Thiên Môn (arc 5 mới lộ), KHÔNG danh tính nội gián, KHÔNG Tô Vân Nhi phản bội.
// Số liệu cân bằng: V113 + GoldenCoreBalanceTest. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-139).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ blood_sand_beast_warden: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "blood_sand_beast_warden" && progress.blood_sand_beast_warden < 1) {
            progress.blood_sand_beast_warden += 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.blood_sand_beast_warden >= 1
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(22000);
    }
})
