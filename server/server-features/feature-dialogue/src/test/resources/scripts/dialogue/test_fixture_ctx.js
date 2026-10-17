// test_fixture_ctx.js — script dùng ctx chỉ-đọc cho GetDialogueNodeUseCaseTest (dòng 65(c)).
({
    start: {
        text_key: ctx.item_count("test_item") > 0 ? "dialogue.test.has_item" : "dialogue.test.greeting",
        options: [ { label_key: "dialogue.option.bye", action: "end" } ]
    },
    status: {
        text_key: "dialogue.test.status." + ctx.quest_status("test_quest"),
        options: []
    }
})
