// test_fixture_npc.js — script dùng cho *UseCaseTest, KHÔNG phải nội dung sản phẩm
// (report_to_outer_sect.js). Mục tiêu loại THỨ HAI: "đi gặp một NPC".
//
// Cố ý viết theo đúng khuôn của bản sản phẩm — tiến độ là một CỜ, không phải bộ đếm — vì sự kiện
// `npc_talked` lặp lại mỗi lần người chơi mở lời (xem QuestFacade.onNpcTalked).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "npc_talked" && event.npc_code === "senior_sister") {
            progress.reported = true;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(200);
    }
})
