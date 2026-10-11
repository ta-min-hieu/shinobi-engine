// Script CỐ Ý SAI cho QuestScriptRunnerHookContractTest: on_accept lưu tiến độ đúng rồi trao thưởng —
// thưởng chỉ được trao ở on_claim, nên từ 2026-10-11 đây là lỗi script.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ killed: 0 }));
        ctx.give_exp(500);
    },
    on_event(ctx, event) {},
    on_claim(ctx) {}
})
