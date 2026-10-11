// test_fixture_wrong_hook.js — script CỐ Ý SAI, chỉ dùng cho QuestScriptRunnerHookContractTest. KHÔNG
// phải nội dung sản phẩm.
//
// Mỗi nhánh gọi một hàm `ctx.*` ở hook mà hiệu ứng của nó KHÔNG được dùng. Trước 2026-10-11 những lời
// gọi này bị bỏ qua im lặng; nay hàm đó không tồn tại ở hook đó nên chúng là lỗi script.
//
// ⚠️ Mỗi nhánh gọi một callback HỢP LỆ của hook đó trước rồi mới tới lời gọi sai chỗ — cùng lý do với
// test_fixture_broken.js.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({}));
    },

    on_event(ctx, event) {
        var name = JSON.parse(ctx.progress_json)["case"];
        ctx.save_progress(JSON.stringify({ "case": name, seen: true }));
        if (name === "give_exp") { ctx.give_exp(10); }
        if (name === "give_item") { ctx.give_item("kunai", 1); }
    },

    on_claim(ctx) {
        var name = JSON.parse(ctx.progress_json)["case"];
        ctx.give_exp(10);
        if (name === "complete") { ctx.complete(); }
        if (name === "save_progress") { ctx.save_progress(JSON.stringify({})); }
        if (name === "take_item") { ctx.take_item("kunai", 1); }
    }
})
