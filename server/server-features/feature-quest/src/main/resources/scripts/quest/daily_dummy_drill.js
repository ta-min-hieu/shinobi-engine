// daily_dummy_drill.js — NHIỆM VỤ NGÀY "Luyện Công Sớm" (ADR-055, Điều 27 — nội dung là script).
//
// STORY: arc-1 §7 (Nhiệm vụ ngày — công việc tông môn) — STORY.md §8 quy tắc 5: nhiệm vụ ngày cũng
// phải có lý do tường thuật, nhưng không cần cốt truyện riêng. Đệ tử tạp dịch mỗi sáng ra bãi mộc
// nhân luyện tay; Chu Hoành giao việc, không có gì sâu hơn.
//
// Lặp lại mỗi ngày: `quest_template.repeat_policy = 'DAILY'` (V59). Làm mới lúc 0h giờ Việt Nam —
// Java lo phần đó (DailyReset), script không biết gì về ngày giờ.
//
// ⚠️ Ba con số (5 mộc nhân, 150 EXP, 30 linh thạch) là ĐỀ XUẤT CÂN BẰNG ban đầu (ADR-055 D6), chưa
// playtest. Đổi chúng ở đây, không ở Java.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "training_dummy") {
            progress.felled = (progress.felled || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.felled >= 5) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(150);
        ctx.give_gold(30);
    }
})
