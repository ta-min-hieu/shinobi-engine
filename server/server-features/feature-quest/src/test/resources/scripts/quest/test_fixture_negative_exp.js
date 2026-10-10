// test_fixture_negative_exp.js — script CỐ Ý SAI, chỉ dùng cho QuestScriptRunnerTest. KHÔNG phải
// nội dung sản phẩm.
//
// `ctx.give_exp` với số âm. Cột `character.exp` KHÔNG có ràng buộc CHECK, còn `Character` (domain)
// từ chối exp âm ngay trong constructor — nên một lần trừ EXP lọt xuống DB làm mọi lần đọc nhân vật
// đó ném lỗi: không vào được world, và cả danh sách nhân vật của tài khoản cũng không liệt kê nổi.
//
// ⚠️ Gọi một `give_exp` HỢP LỆ trước rồi mới tới số âm, cùng lý do với test_fixture_broken.js: tổng
// vẫn dương (100 - 30 = 70), nên chỉ phép kiểm TỪNG lần gọi mới bắt được — kiểm tổng thì không.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({}));
    },

    on_event(ctx) {
        ctx.save_progress(ctx.progress_json);
    },

    on_claim(ctx) {
        ctx.give_exp(100);
        ctx.give_exp(-30);
    }
})
