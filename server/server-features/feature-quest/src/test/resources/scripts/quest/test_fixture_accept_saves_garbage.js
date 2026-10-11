// Script CỐ Ý SAI cho QuestScriptRunnerHookContractTest: on_accept lưu một chuỗi KHÔNG phải JSON.
({
    on_accept(ctx) {
        ctx.save_progress("killed=0");
    },
    on_event(ctx, event) {},
    on_claim(ctx) {}
})
