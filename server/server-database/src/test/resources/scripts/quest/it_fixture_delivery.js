// it_fixture_delivery.js — script CHỈ dùng cho QuestIntegrationTest (Postgres thật), KHÔNG phải nội
// dung sản phẩm, KHÔNG có dòng quest_template nào trong migration trỏ tới nó: test tự INSERT dòng đó.
// Mục tiêu loại THỨ BA — "mang vật X tới NPC Y" (2026-09-11).
//
// Vì sao HAI món khác loại: lấy được món đầu rồi mới thiếu món sau là cách duy nhất chứng minh
// rollback trên DB THẬT — lần trừ `minor_healing_pill` ĐÃ chạy câu SQL, và phải biến mất.
// Hai mã vật phẩm là mã thật đã seed (`minor_healing_pill` V36, `kunai` V4) — không bịa mã.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ delivered: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "npc_talked" && event.npc_code === "senior_sister" && !progress.delivered) {
            progress.delivered = true;
            ctx.take_item("minor_healing_pill", 2);
            ctx.take_item("kunai", 1);
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.delivered) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(10);
    }
})
