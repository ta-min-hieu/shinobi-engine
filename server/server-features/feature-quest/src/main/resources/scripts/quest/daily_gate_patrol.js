// daily_gate_patrol.js — NHIỆM VỤ NGÀY "Tuần Tra Cổng Nội Môn" (ADR-135, Điều 27 — nội dung là script).
//
// STORY: arc-3 §7 (Nhiệm vụ ngày — công việc tông môn: tuần tra). Sau vụ lịch khảo hạch bị lộ, tông môn đặt lệ tuần
// tra cổng nội môn mỗi ngày, dẹp tế đồ trà trộn (`blood_sand_infiltrator`, @248/@264). Không nhắc danh tính nội gián
// (§8 quy tắc 2 — giữ tới arc 5).
//
// ⚠️ Số (3 tế đồ, 5.000 EXP, 400 linh thạch) là ĐỀ XUẤT CÂN BẰNG (ADR-135), chưa playtest.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "blood_sand_infiltrator") {
            progress.felled = (progress.felled || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.felled >= 3) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(5000);
        ctx.give_gold(400);
    }
})
