// night_watch_bandit.js — nội dung quest "night_watch_bandit" (Điều 27, GAMEPLAY.md §9.3),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-1 §6 (Tạp Dịch) — CAO TRÀO của arc, bắt buộc khai theo STORY.md §8 quy tắc 1.
// Nguyên văn §6 arc 1: "một tán tu cường đạo (quái `wandering_bandit`) lẻn vào trộm linh thảo,
// đúng phiên người chơi trực đêm. Đánh đuổi được hắn → Chu Hoành để ý → được đặc cách dự khảo
// hạch → thức tỉnh linh căn (Lv10)."
//
// ⚠️ ĐÂY LÀ QUEST THỨ HAI THEO DÕI GIẾT QUÁI của dự án. Nó chỉ chạy được vì
// `ProcessMonsterKilledUseCase` đã được tổng quát hoá TRƯỚC (2026-09-05) — bản cũ hardcode đúng
// một mã quest nên script này sẽ không bao giờ nhận được `on_event`. Xem feature-quest/CLAUDE.md.
//
// ⚠️ Java KHÔNG biết quest này quan tâm con quái nào — chính dòng `event.monster_code` dưới đây là
// nơi duy nhất biết điều đó. Thêm quest mới vì thế không phải sửa một dòng Java nào.
//
// ⚠️ Mã quái `wandering_bandit` và mã vật phẩm `enhance_protection_charm` là KHOÁ NGHIỆP VỤ có
// dòng DB thật trỏ vào (`monster_template.code`, `item_template.code`) — ADR-038 D5 cấm đổi. Tên
// hiển thị tu tiên ("tán tu cường đạo", "Hộ Linh Phù") nằm ở bundle i18n, không nằm ở đây.
//
// ⚠️ BA CON SỐ DƯỚI ĐÂY LÀ CÂN BẰNG, KHÔNG PHẢI TƯỜNG THUẬT (1 lần đuổi, 800 EXP, 1 Hộ Linh Phù).
// Chọn theo tương quan với `kill_training_dummies` (3 cọc gỗ đứng yên = 500 EXP): một tán tu biết
// đánh trả, aggro 6m, atk gấp đôi cọc — đáng hơn, nhưng chỉ cần đuổi MỘT lần vì §6 viết là một
// lần chạm trán, không phải đi săn. Đổi ba số này là quyết định gameplay riêng, cần playtest riêng.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ repelled: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "wandering_bandit") {
            progress.repelled = (progress.repelled || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.repelled >= 1) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(800);
        ctx.give_item("enhance_protection_charm", 1);
    }
})
