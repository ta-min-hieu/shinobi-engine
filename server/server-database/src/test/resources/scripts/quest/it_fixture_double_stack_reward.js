// it_fixture_double_stack_reward.js — script CHỈ dùng cho QuestClaimStackRoomIntegrationTest
// (Postgres thật), KHÔNG phải nội dung sản phẩm, KHÔNG có dòng quest_template nào trong migration trỏ
// tới nó: test tự INSERT dòng đó.
//
// Vì sao trao CÙNG một mã HAI lần (2026-10-02): câu hỏi "đủ chỗ" từng soi túi TRƯỚC khi trao, nên
// cả hai lần 50 phi đao đều thấy chồng 49 "còn chỗ" — trong khi lần đầu đã lấp chồng đó tới 99 và
// lần sau buộc phải mở ô mới. `kunai` là mã thật đã seed (V4, max_stack 99) — không bịa mã.
({
    on_accept(ctx) {
        ctx.save_progress("{}");
    },

    on_event(ctx, event) {
    },

    on_claim(ctx) {
        ctx.give_exp(10);
        ctx.give_item("kunai", 50);
        ctx.give_item("kunai", 50);
    }
})
