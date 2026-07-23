// kill_training_dummies.js — nội dung quest "kill_training_dummies" (Điều 27, GAMEPLAY.md §9.3),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị (Java
// không đọc return value, chỉ đọc lại hiệu ứng từ ctx sau khi eval xong).
//
// ctx.progress_json là CHUỖI (không phải object) — script tự JSON.parse/JSON.stringify (GraalJS
// built-in, KHÔNG cần thư viện JSON phía Java). event.type/event.monster_code là dữ liệu thuần.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ killed: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "training_dummy") {
            progress.killed = (progress.killed || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.killed >= 3) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(500);
        ctx.give_item("kunai", 5);
    }
})
