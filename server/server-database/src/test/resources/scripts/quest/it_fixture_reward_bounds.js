// it_fixture_reward_bounds.js — script CỐ Ý SAI, chỉ dùng cho QuestRewardItemBoundsIntegrationTest
// (Postgres thật). KHÔNG phải nội dung sản phẩm, KHÔNG có dòng quest_template nào trong migration trỏ
// tới nó: test tự INSERT dòng đó.
//
// Mỗi nhánh là MỘT lời `give_item` ở biên của miền số lượng/mã. Test chọn nhánh bằng cách ghi thẳng
// `progress_json` (`{"case":"negative"}`) — chuỗi đó đục với Java.
//
// `give_exp(10)` chạy TRƯỚC ở mọi nhánh: nó là thứ cho thấy lượt lĩnh có bị bỏ TRỌN hay không (EXP
// không đổi), thay vì chỉ thiếu mỗi món sai. `kunai` (max_stack 99) và `wooden_sword` (max_stack 1)
// là mã thật đã seed — không bịa mã, trừ đúng nhánh `unknown_code`.
({
    on_accept(ctx) {
        ctx.save_progress("{}");
    },

    on_event(ctx, event) {
    },

    on_claim(ctx) {
        var name = JSON.parse(ctx.progress_json)["case"];
        ctx.give_exp(10);
        var cases = {
            negative() { ctx.give_item("kunai", -5); },
            zero() { ctx.give_item("kunai", 0); },
            max_int_stackable() { ctx.give_item("kunai", 2147483647); },
            max_int_stackable_twice() { ctx.give_item("kunai", 2147483647); ctx.give_item("kunai", 2147483647); },
            max_int_unstackable() { ctx.give_item("wooden_sword", 2147483647); },
            unknown_code() { ctx.give_item("no_such_item_code", 1); },
            empty_code() { ctx.give_item("", 1); }
        };
        cases[name]();
    }
})
