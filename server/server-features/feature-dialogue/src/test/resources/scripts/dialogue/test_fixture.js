// test_fixture.js — script dùng cho GetDialogueNodeUseCaseTest, KHÔNG phải nội dung sản phẩm.
({
    start: {
        text_key: "dialogue.test.greeting",
        options: [
            { label_key: "dialogue.option.ask", action: "more" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    more: {
        text_key: "dialogue.test.more",
        options: [
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    }
})
