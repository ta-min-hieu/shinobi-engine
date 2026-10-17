// test_fixture_ctx_bad_argument.js — gọi ctx với mã không phải chuỗi: lỗi script, không phải "0 món".
({
    start: {
        text_key: ctx.item_count(undefined) > 0 ? "dialogue.test.has_item" : "dialogue.test.greeting",
        options: []
    }
})
