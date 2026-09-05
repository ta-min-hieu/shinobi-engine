// town_elder.js — nội dung hội thoại NPC "town_elder" (Điều 27, GAMEPLAY.md §9.2), KHÔNG hardcode
// Java. Trả về TOÀN BỘ cây node (object literal, key = nodeId) — Java (GetDialogueNodeUseCase)
// trích đúng 1 node cần dùng mỗi lần gọi, không giữ state giữa các lần (script tự chứa dữ liệu).
//
// STORY: arc-1 §6 (Tạp Dịch) — bắt buộc khai theo STORY.md §8 quy tắc 1.
// Nhân vật: trưởng lão Chu Hoành (STORY.md §5), tông môn Thanh Hư (STORY.md §4.1).
//
// ⚠️ NHỊP HÉ LỘ (STORY.md §8 quy tắc 2): arc 1 chỉ được GIEO MẦM. Chu Hoành ĐƯỢC nói về tông môn,
// về thang cảnh giới, về chuyện chính ông kẹt ở Kim Đan. Ông KHÔNG được nhắc tới Thiên Môn, tới
// chân tướng Thiên Địa Đại Kiếp, hay tới mảnh ngọc cổ — những thứ đó thuộc arc 3 và arc 5. Thêm
// nhánh mới vào đây thì kiểm lại ranh giới đó trước.
//
// ⚠️ TÊN KHOÁ LÀ DI SẢN: node `about_town` và khoá `dialogue.town_elder.about_town` ra đời thời bối
// cảnh làng ninja. ADR-038 D7.2 cấm đổi khoá (khoá đi thẳng qua mạng trong text_key, và
// DialogueIntegrationTest kiểm đúng chuỗi này), nên khoá còn chữ "town" trong khi nội dung đã là
// tông môn tu tiên. Đọc theo nội dung, đừng đọc theo tên khoá.
//
// text_key/label_key là khoá i18n (client tự tra bản dịch), KHÔNG phải text hiển thị thẳng.
// action của option là 1 trong: tên node khác trong cây này (điều hướng), "open_shop" (mở cửa
// hàng toàn cục — GAMEPLAY.md §7.2), "end" (kết thúc hội thoại), "accept_quest:<code>" (nhận quest
// — GAMEPLAY.md §9.3, gateway gọi QuestFacade.acceptQuest, feature-dialogue không biết feature-quest).
//
// ⚠️ Bốn lựa chọn của node `start` GIỮ NGUYÊN cả thứ tự lẫn action: `option_index` là VỊ TRÍ trong
// node (dialogue.proto), nên chèn thêm một lựa chọn vào giữa là đổi ý nghĩa của mọi index phía sau.
// Nội dung mới của arc 1 vì vậy mọc XUỐNG DƯỚI qua `about_town`, không chen ngang ở `start`.
({
    start: {
        text_key: "dialogue.town_elder.greeting",
        options: [
            { label_key: "dialogue.option.ask_about_town", action: "about_town" },
            { label_key: "dialogue.option.accept_quest_kill_dummies", action: "accept_quest:kill_training_dummies" },
            { label_key: "dialogue.option.open_shop", action: "open_shop" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // "Tông môn là gì" — giới thiệu Thanh Hư Tông và thân phận tạp dịch của người chơi.
    about_town: {
        text_key: "dialogue.town_elder.about_town",
        options: [
            { label_key: "dialogue.option.ask_about_realm", action: "about_realm" },
            { label_key: "dialogue.option.ask_about_elder", action: "about_elder" },
            { label_key: "dialogue.option.ask_night_watch", action: "night_watch" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // CAO TRÀO arc 1 (STORY.md §6): phiên trực đêm — quest thứ hai của game. Mọc xuống dưới qua
    // `about_town` chứ KHÔNG chen vào `start`: 4 lựa chọn của `start` bị khoá bởi option_index
    // (xem đầu file + TownElderDialogueTreeTest).
    //
    // Nhịp hé lộ (§8 quy tắc 2): Chu Hoành chỉ nói tới chuyện trước mắt — linh thảo bị trộm, ai đó
    // lảng vảng ngoài rào. Ông KHÔNG được nhắc Huyết Sa Giáo (arc 2 mới lộ), càng không nhắc
    // Thiên Môn hay mảnh ngọc cổ.
    night_watch: {
        text_key: "dialogue.town_elder.night_watch",
        options: [
            { label_key: "dialogue.option.accept_quest_night_watch", action: "accept_quest:night_watch_bandit" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // Thang cảnh giới (ADR-038 D2) — dạy người chơi đọc con số cấp độ của chính mình, và đặt mốc
    // "Luyện Khí tầng 5 thì đo được linh căn" khớp điều kiện chọn linh căn ở cấp 10 (GDD §5).
    about_realm: {
        text_key: "dialogue.town_elder.about_realm",
        options: [
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // Động cơ của Chu Hoành (STORY.md §5) — cũng chính là luận đề của cả game: linh căn quyết định
    // đi NHANH tới đâu, không quyết định đi ĐƯỢC tới đâu.
    about_elder: {
        text_key: "dialogue.town_elder.about_elder",
        options: [
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    }
})
