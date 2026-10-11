// test_fixture_boundary.js — script CỐ Ý SAI, chỉ dùng cho QuestScriptRunnerBoundaryTest. KHÔNG phải
// nội dung sản phẩm.
//
// Mỗi nhánh gọi MỘT hàm `ctx.*` với MỘT giá trị biên. Test chọn nhánh qua `progress_json`
// (`{"case":"give_item_negative"}`) — chuỗi đó đục với Java, nên không cần thêm đường truyền nào.
//
// ⚠️ Mọi nhánh đều gọi một callback HỢP LỆ trước rồi mới tới lời gọi biên, cùng lý do với
// test_fixture_broken.js: một nhánh chỉ có đúng lời gọi sai sẽ xanh kể cả khi Java kiểm TỔNG thay vì
// kiểm TỪNG lần gọi.
//
// Hai hook dùng chung một bảng nhánh; từ 2026-10-11 mỗi hàm `ctx.*` chỉ có ở hook của nó (give_exp/
// give_item ở on_claim; save_progress/complete/take_item ở on_event) — test tự chọn hook đúng cho từng nhánh.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({}));
    },

    on_event(ctx, event) {
        this.run_case(ctx);
    },

    on_claim(ctx) {
        this.run_case(ctx);
    },

    run_case(ctx) {
        var name = JSON.parse(ctx.progress_json)["case"];
        var cases = {
            // ----- give_item: số lượng -----
            give_item_negative() { ctx.give_item("kunai", 1); ctx.give_item("kunai", -5); },
            give_item_zero() { ctx.give_item("kunai", 1); ctx.give_item("kunai", 0); },
            give_item_negative_zero() { ctx.give_item("kunai", 1); ctx.give_item("kunai", -0); },
            give_item_fraction() { ctx.give_item("kunai", 1); ctx.give_item("kunai", 1.5); },
            give_item_nan() { ctx.give_item("kunai", 1); ctx.give_item("kunai", NaN); },
            give_item_infinity() { ctx.give_item("kunai", 1); ctx.give_item("kunai", Infinity); },
            give_item_numeric_string() { ctx.give_item("kunai", 1); ctx.give_item("kunai", "5"); },
            give_item_beyond_int() { ctx.give_item("kunai", 1); ctx.give_item("kunai", 2147483648); },
            give_item_null_quantity() { ctx.give_item("kunai", 1); ctx.give_item("kunai", null); },
            give_item_missing_quantity() { ctx.give_item("kunai", 1); ctx.give_item("kunai"); },
            give_item_no_arguments() { ctx.give_item("kunai", 1); ctx.give_item(); },
            // ----- give_item: mã vật phẩm -----
            give_item_null_code() { ctx.give_item("kunai", 1); ctx.give_item(null, 1); },
            give_item_undefined_code() { ctx.give_item("kunai", 1); ctx.give_item(undefined, 1); },
            give_item_empty_code() { ctx.give_item("kunai", 1); ctx.give_item("", 1); },
            give_item_blank_code() { ctx.give_item("kunai", 1); ctx.give_item("   ", 1); },
            give_item_nul_code() { ctx.give_item("kunai", 1); ctx.give_item("ku\u0000nai", 1); },
            give_item_numeric_code() { ctx.give_item("kunai", 1); ctx.give_item(123, 1); },
            // ----- give_item: hợp lệ ở tầng script (tầng túi đồ mới là nơi phán quyết) -----
            give_item_max_int() { ctx.give_item("kunai", 2147483647); },
            give_item_whole_double() { ctx.give_item("kunai", 2.0); },
            give_item_same_code_twice() { ctx.give_item("kunai", 2147483647); ctx.give_item("kunai", 2147483647); },

            // ----- give_exp -----
            give_exp_fraction() { ctx.give_exp(10); ctx.give_exp(1.5); },
            give_exp_nan() { ctx.give_exp(10); ctx.give_exp(NaN); },
            give_exp_infinity() { ctx.give_exp(10); ctx.give_exp(Infinity); },
            give_exp_numeric_string() { ctx.give_exp(10); ctx.give_exp("5"); },
            give_exp_null() { ctx.give_exp(10); ctx.give_exp(null); },
            give_exp_no_arguments() { ctx.give_exp(10); ctx.give_exp(); },
            give_exp_beyond_long() { ctx.give_exp(10); ctx.give_exp(1e19); },
            // 2^62 hai lần = 2^63, vừa quá Long.MAX_VALUE — từng lần thì hợp lệ, TỔNG mới tràn.
            give_exp_sum_overflows() { ctx.give_exp(4611686018427387904); ctx.give_exp(4611686018427387904); },
            give_exp_zero() { ctx.give_exp(10); ctx.give_exp(0); },

            // ----- save_progress -----
            save_progress_null() { ctx.save_progress("{}"); ctx.save_progress(null); },
            save_progress_undefined() { ctx.save_progress("{}"); ctx.save_progress(JSON.stringify(undefined)); },
            save_progress_number() { ctx.save_progress("{}"); ctx.save_progress(42); },
            save_progress_object() { ctx.save_progress("{}"); ctx.save_progress({ counter: 1 }); },
            save_progress_no_arguments() { ctx.save_progress("{}"); ctx.save_progress(); },
            save_progress_nul() { ctx.save_progress("{}"); ctx.save_progress("{\"a\":\"\u0000\"}"); },

            // ----- take_item (chỉ on_event) -----
            take_item_zero() { ctx.take_item("kunai", 1); ctx.take_item("kunai", 0); },
            take_item_fraction() { ctx.take_item("kunai", 1); ctx.take_item("kunai", 1.5); },
            take_item_nan() { ctx.take_item("kunai", 1); ctx.take_item("kunai", NaN); },
            take_item_numeric_string() { ctx.take_item("kunai", 1); ctx.take_item("kunai", "5"); },
            take_item_missing_quantity() { ctx.take_item("kunai", 1); ctx.take_item("kunai"); },
            take_item_null_code() { ctx.take_item("kunai", 1); ctx.take_item(null, 1); },
            take_item_empty_code() { ctx.take_item("kunai", 1); ctx.take_item("", 1); },
            take_item_nul_code() { ctx.take_item("kunai", 1); ctx.take_item("ku\u0000nai", 1); },
            take_item_max_int() { ctx.take_item("kunai", 2147483647); }
        };
        cases[name]();
        // save_progress chỉ có ở on_event — ở on_claim gọi nó là lỗi script (đúng luật, nhưng không phải
        // thứ các nhánh trên muốn đo).
        if (ctx.save_progress) {
            ctx.save_progress(JSON.stringify({ "case": name, done: true }));
        }
    }
})
