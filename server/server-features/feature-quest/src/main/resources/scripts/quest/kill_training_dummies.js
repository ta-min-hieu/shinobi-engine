// kill_training_dummies.js — nội dung quest "kill_training_dummies" (Điều 27, GAMEPLAY.md §9.3),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị (Java
// không đọc return value, chỉ đọc lại hiệu ứng từ ctx sau khi eval xong).
//
// STORY: arc-1 §6 (Tạp Dịch) — bắt buộc khai theo STORY.md §8 quy tắc 1.
// Việc vặt đầu tiên trưởng lão Chu Hoành giao cho đệ tử tạp dịch: ra bãi mộc nhân luyện tay.
// Tên hiển thị "Đập Mộc Nhân" nằm ở khoá i18n `quest.name.kill_training_dummies`, không nằm ở đây.
//
// ⚠️ MÃ quest, mã quái (`training_dummy`) và mã phần thưởng (`kunai`) là mã cũ thời bối cảnh ninja
// và GIỮ NGUYÊN — chúng là khoá nghiệp vụ có dòng DB thật trỏ vào (quest_template.code,
// monster_template.code, item_template.code); ADR-038 D5 cấm đổi. Chỉ tên HIỂN THỊ đổi theo bối
// cảnh tu tiên: `training_dummy` đọc là "mộc nhân luyện công", `kunai` đọc là "Phi Đao".
//
// ⚠️ Ba con số dưới đây (3 lần giết, 500 EXP, 5 phi đao) KHÔNG đổi theo đợt re-theme: chúng là cân
// bằng, không phải tường thuật. Đổi chúng là một quyết định gameplay riêng, cần playtest riêng.
//
// ctx.progress_json là CHUỖI (không phải object) — script tự JSON.parse/JSON.stringify (GraalJS
// built-in, KHÔNG cần thư viện JSON phía Java). event.type/event.monster_code là dữ liệu thuần.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ killed: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "training_dummy") {
            progress.killed = (progress.killed || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.killed >= 3) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(500);
        ctx.give_item("kunai", 5);
    }
})
