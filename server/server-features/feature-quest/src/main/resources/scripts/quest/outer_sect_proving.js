// outer_sect_proving.js — nội dung quest "outer_sect_proving" (Điều 27, GAMEPLAY.md §9.6).
//
// STORY: arc-2 §6 — *"đứng vững ở ngoại môn"*. Nhịp áp chót: người chơi chứng minh mình đủ tư cách
// dự khảo hạch Trúc Cơ, bằng việc tự dọn sạch rìa Hoang Vực.
//
// Ba con yêu thú, không phải một: đây là nhịp CÀY có chủ đích, đối lại với nhịp sau (đúng một trận,
// một lượt trình diện). Cùng lý do `awakening_trial` dừng ở N = 1 còn ba quest trước nó thì không.
//
// ⚠️ Có hồi máu ngoài chiến đấu từ ADR-046, nên ba trận liên tiếp KHÔNG còn là án tử như arc 1 —
// nhưng người chơi vẫn phải nghỉ giữa các trận, và chỗ nghỉ phải ngoài tầm ĐUỔI (leash) của con
// vừa đánh thức, không phải ngoài tầm aggro. Bài học 2026-09-16, trả giá bằng một cái chết.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "wasteland_beast") {
            progress.felled = progress.felled + 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.felled >= 3
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(5000);
    }
})
