// test_fixture.js — script dùng cho *UseCaseTest, KHÔNG phải nội dung sản phẩm (kill_training_dummies.js).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ counter: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "training_dummy") {
            progress.counter = (progress.counter || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.counter >= 2) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(100);
        ctx.give_item("test_item", 3);
    }
})
