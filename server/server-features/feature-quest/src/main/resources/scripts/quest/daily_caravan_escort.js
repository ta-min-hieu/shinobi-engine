// daily_caravan_escort.js — NHIỆM VỤ NGÀY "Áp Tải Xe Linh Thảo" (ADR-114, Điều 27 — nội dung là script).
//
// STORY: arc-1 §7 (Nhiệm vụ ngày — công việc tông môn). Chu Hoành nhờ đưa xe linh thảo của tông môn tới trạm Linh
// Mạch; đường trục có cường đạo lảng vảng nên phải có người đi kèm. Không nhắc gì vượt nhịp arc 1.
//
// Mục tiêu là SỰ KIỆN do server sinh (ADR-114 D3): `escort_arrived` khi xe tới nơi, `escort_failed` khi xe vỡ. Tuyến
// (x = 0 → 82, máu, tốc độ) là tham số ở bảng `escort_route` (V94), không ở đây. Xe vỡ thì server tự bỏ quest về "chưa
// nhận" (đường ADR-054) — script chỉ cần xoá dấu, nhận lại là đi lại từ đầu.
//
// ⚠️ Thưởng (400 EXP, 80 linh thạch) là ĐỀ XUẤT CÂN BẰNG ban đầu, chưa playtest.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ arrived: false }));
    },

    on_event(ctx, event) {
        if (event.quest_code !== "daily_caravan_escort") {
            ctx.save_progress(ctx.progress_json);
            return;
        }
        if (event.type === "escort_arrived") {
            ctx.save_progress(JSON.stringify({ arrived: true }));
            ctx.complete();
        } else if (event.type === "escort_failed") {
            ctx.save_progress(JSON.stringify({ arrived: false }));
        } else {
            ctx.save_progress(ctx.progress_json);
        }
    },

    on_claim(ctx) {
        ctx.give_exp(400);
        ctx.give_gold(80);
    }
})
