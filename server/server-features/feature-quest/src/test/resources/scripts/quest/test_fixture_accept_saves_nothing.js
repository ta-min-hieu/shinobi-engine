// Script CỐ Ý SAI cho QuestScriptRunnerHookContractTest: on_accept KHÔNG lưu tiến độ. Trước 2026-10-11
// quest vẫn thành IN_PROGRESS rồi kẹt mãi vì on_event không parse được tiến độ rỗng.
({
    on_accept(ctx) {},
    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        ctx.save_progress(JSON.stringify(progress));
    },
    on_claim(ctx) {}
})
