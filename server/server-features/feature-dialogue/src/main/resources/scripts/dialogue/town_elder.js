// town_elder.js — nội dung hội thoại NPC "town_elder" (Điều 27, GAMEPLAY.md §9.2), KHÔNG hardcode
// Java. Trả về TOÀN BỘ cây node (object literal, key = nodeId) — Java (GetDialogueNodeUseCase)
// trích đúng 1 node cần dùng mỗi lần gọi, không giữ state giữa các lần (script tự chứa dữ liệu).
//
// text_key/label_key là khoá i18n (client tự tra bản dịch), KHÔNG phải text hiển thị thẳng.
// action của option là 1 trong: tên node khác trong cây này (điều hướng), "open_shop" (mở cửa
// hàng toàn cục — GAMEPLAY.md §7.2), "end" (kết thúc hội thoại).
({
    start: {
        text_key: "dialogue.town_elder.greeting",
        options: [
            { label_key: "dialogue.option.ask_about_town", action: "about_town" },
            { label_key: "dialogue.option.open_shop", action: "open_shop" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    about_town: {
        text_key: "dialogue.town_elder.about_town",
        options: [
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    }
})
