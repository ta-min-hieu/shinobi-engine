// daily_inner_sparring.js — NHIỆM VỤ NGÀY "Đấu Tập Nội Môn" (ADR-135, Điều 27 — nội dung là script).
//
// STORY: arc-3 §7 (Nhiệm vụ ngày — công việc tông môn). Đệ tử nội môn mỗi ngày phải đấu tập với cỗ Trắc Nội Môn
// Thạch Nhân ở sân khảo hạch phía đông (`inner_trial_construct`, @232) — cỗ máy tông môn sửa lại để đo sức bền. Không
// nhắc nội gián hay Thiên Môn (§8 quy tắc 2: nhiệm vụ ngày không có cốt truyện riêng).
//
// ⚠️ Số (2 lượt, 4.000 EXP, 300 linh thạch) là ĐỀ XUẤT CÂN BẰNG (ADR-135), chưa playtest.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "inner_trial_construct") {
            progress.felled = (progress.felled || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.felled >= 2) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(4000);
        ctx.give_gold(300);
    }
})
