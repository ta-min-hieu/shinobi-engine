// test_fixture_malformed.js — node hỏng có chủ đích cho GetDialogueNodeUseCaseTest (dòng 77(e)).
({
    missing_text: { options: [] },
    text_not_string: { text_key: 42, options: [] },
    options_missing: { text_key: "dialogue.test.greeting" },
    option_missing_action: {
        text_key: "dialogue.test.greeting",
        options: [ { label_key: "dialogue.option.bye" } ]
    }
})
