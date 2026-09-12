// test_fixture_delivery.js — script dùng cho *UseCaseTest/QuestScriptRunnerTest, KHÔNG phải nội dung
// sản phẩm. Mục tiêu loại THỨ BA: "mang vật X tới NPC Y" (2026-09-11).
//
// Khuôn đúng của loại mục tiêu này, cũng là khuôn một script sản phẩm nên chép:
//   - mốc là `npc_talked` (lúc MỞ LỜI), y như "đi gặp NPC" — không phải một lựa chọn thoại;
//   - tiến độ là một CỜ (`delivered`), và điều kiện có `!progress.delivered` để một lần mở lời
//     nữa KHÔNG đòi đồ lần hai (quan trọng khi giao đồ chưa phải bước cuối của quest);
//   - `ctx.take_item` là ĐIỀU KIỆN của cả lần chạy hook: thiếu một món thì cờ `delivered = true` ở
//     đây cũng không bao giờ được ghi xuống — Java rollback hết.
//
// Ba nhánh còn lại chỉ để test những đường hỏng: script ném lỗi giữa chừng, số lượng âm, và
// take_item ở on_claim (hàm đó KHÔNG tồn tại ngoài on_event).
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ delivered: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "npc_talked" && event.npc_code === "senior_sister" && !progress.delivered) {
            progress.delivered = true;
            ctx.take_item("sealed_letter", 1);
            ctx.take_item("spirit_herb", 2);
        }
        if (event.type === "npc_talked" && event.npc_code === "broken_npc") {
            progress.delivered = true;
            ctx.take_item("sealed_letter", 1);
            ctx.save_progress(JSON.stringify(progress));
            ctx.complete();
            throw new Error("script hỏng giữa chừng — mọi hiệu ứng phía trên phải bị bỏ");
        }
        if (event.type === "npc_talked" && event.npc_code === "negative_npc" && !progress.delivered) {
            progress.delivered = true;
            ctx.take_item("sealed_letter", -1);
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.delivered) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.take_item("sealed_letter", 1);
        ctx.give_exp(50);
    }
})
