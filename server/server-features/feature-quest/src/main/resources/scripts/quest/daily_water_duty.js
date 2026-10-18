// daily_water_duty.js — NHIỆM VỤ NGÀY "Gánh Nước" (ADR-055, Điều 27 — nội dung là script).
//
// STORY: arc-1 §7 (Nhiệm vụ ngày — công việc tông môn: gánh nước). Gánh nước ra sân luyện công cho
// Tô Vân Nhi (`senior_sister`, đứng ở x=2 giữa tông môn và bãi mộc nhân), rồi về báo Chu Hoành.
//
// Hai bước, đúng luật "người GIAO việc không thể là mốc của bước ĐẦU" (feature-quest/CLAUDE.md):
// mở lời với Chu Hoành bắn `npc_talked` TRƯỚC khi quest tồn tại, nên bước đầu phải là gặp NGƯỜI
// KHÁC. Bước hai chỉ tính khi bước một đã xong (`progress.asked` — tên trường theo quy ước bước "hỏi trước" của QuestFuzzSpec) — thứ tự sống trong script.
//
// ⚠️ Số (100 EXP, 20 linh thạch) là ĐỀ XUẤT CÂN BẰNG ban đầu (ADR-055 D6), chưa playtest.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ asked: false, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "npc_talked") {
            if (event.npc_code === "senior_sister") {
                progress.asked = true;
            } else if (event.npc_code === "town_elder" && progress.asked) {
                progress.reported = true;
            }
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(100);
        ctx.give_gold(20);
    }
})
