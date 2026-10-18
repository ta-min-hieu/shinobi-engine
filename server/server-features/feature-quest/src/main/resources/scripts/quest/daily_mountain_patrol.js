// daily_mountain_patrol.js — NHIỆM VỤ NGÀY "Tuần Sơn" (ADR-055, Điều 27 — nội dung là script).
//
// STORY: arc-1 §7 (Nhiệm vụ ngày — công việc tông môn: tuần sơn). Tán tu cường đạo
// (`wandering_bandit`, STORY §7) sống bằng cướp ở rìa linh mạch; tuần sơn là đuổi chúng khỏi rào
// tông môn. Không nhắc Huyết Sa hay bất cứ thứ gì vượt nhịp (§8 quy tắc 2).
//
// ⚠️ Số (3 tán tu, 300 EXP, 60 linh thạch) là ĐỀ XUẤT CÂN BẰNG ban đầu (ADR-055 D6), chưa playtest.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "wandering_bandit") {
            progress.felled = (progress.felled || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.felled >= 3) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(300);
        ctx.give_gold(60);
    }
})
