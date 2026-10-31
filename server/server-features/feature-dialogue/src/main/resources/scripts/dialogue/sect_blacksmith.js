// sect_blacksmith.js — nội dung hội thoại NPC "sect_blacksmith" (Điều 27, GAMEPLAY.md §9.2), ADR-131.
//
// STORY: arc-2 §6 (Ngoại Môn) — người của Luyện Khí Đường (STORY §4.1 liệt các Đường của tông). Vô danh có chủ
// đích, như người trực trạm: một thợ rèn của Đường, không phải nhân vật cốt truyện, nên không tên riêng (§8 quy tắc 3
// — tên riêng mới phải thêm vào §5 cùng PR).
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2): chỉ nói chuyện lò, quặng, phẩm chất món ra lò. Không Thiên Môn, không Huyết Sa.
//
// "open_forge" (ADR-131) mở lò luyện khí — lò ADR-120 chuyển từ Chu Hoành sang đây; server kiểm đứng gần NPC này.
({
    start: {
        text_key: "dialogue.sect_blacksmith.greeting",
        options: [
            { label_key: "dialogue.option.open_forge", action: "open_forge" },
            { label_key: "dialogue.option.ask_forge_quality", action: "about_quality" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    about_quality: {
        text_key: "dialogue.sect_blacksmith.about_quality",
        options: [
            { label_key: "dialogue.option.open_forge", action: "open_forge" },
            { label_key: "dialogue.option.back", action: "start" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    }
})
