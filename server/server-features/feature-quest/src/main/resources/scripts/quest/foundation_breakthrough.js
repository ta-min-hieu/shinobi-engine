// foundation_breakthrough.js — nội dung quest "foundation_breakthrough" (Điều 27, GAMEPLAY.md §9.6).
//
// STORY: arc-2 §6 — nhịp ĐÓNG ARC: *"Trúc Cơ thành công"*. Và đây là NỘI DUNG CUỐI CÙNG CỦA v1.
//
// =============================================================================================
// MỘT TRẬN, VÀ 5.400 EXP CHỌN TỪ RÀNG BUỘC
// =============================================================================================
// MỘT trận, cùng lý do `awakening_trial` (Lv10) chọn N = 1: khảo hạch là một lượt TRÌNH DIỆN, không
// phải một buổi cày. Đối thủ là `foundation_warden` @201 — cỗ máy tông môn nhặt về sửa lại, cùng
// dòng `trial_construct` của khảo hạch Lv10, nâng một bậc. Bạch Kính Chi KHÔNG xuất hiện: §5 khai
// ông ta bị vượt ở arc 3, tiêu trước ở đây là tiêu một nhịp arc 3 không còn chỗ trả.
//
// Ràng buộc EXP: lĩnh thưởng quest này XONG thì người chơi phải đứng ở ĐÚNG Cấp 36 — cấp CUỐI của
// Trúc Cơ (`CultivationRealm.of(36)` = Trúc Cơ tầng 9; Cấp 37 đã sang Tử Phủ, tức arc 3).
//
//   Kagemaru đo được 38.850 ở Cấp 28. Mốc Cấp 36 = 100 × (1+…+35) = 63.000 ⇒ cần 24.150.
//   Năm quest: 4.400 + 4.600 + 4.800 + 5.000 + 5.400 = 24.200, cộng EXP quái rơi dọc đường.
//
// ⚠️ Phép tính này PHẢI được khoá bằng ô test đối chiếu `ExperienceCurve` THẬT, không chép công
// thức — đúng khuôn `QuestIntegrationTest` đã làm cho `awakening_trial`.
// 📌 Ghi chú trong `awakening_trial.js` trỏ tới `AwakeningTrialProgressionTest`; lớp đó KHÔNG tồn
// tại (chốt chặn thật nằm ở `QuestIntegrationTest`). Sửa câu đó cùng đợt này.
//
// =============================================================================================
// HAI BƯỚC CÓ THỨ TỰ
// =============================================================================================
//   1. `monster_killed` foundation_warden — qua khảo hạch
//   2. `npc_talked` town_elder            — Chu Hoành xác nhận Trúc Cơ, arc 2 đóng
//
// ⚠️ KHÔNG hứa gì về arc 3 ở đây. §8 quy tắc 2: vết nứt Thiên Môn, nội gián cấp trưởng lão, và Tô
// Vân Nhi đều là chuyện của các arc sau. Chu Hoành chỉ xác nhận thứ ông thấy được.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ endured: false, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "foundation_warden") {
            progress.endured = true;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.endured
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(5400);
    }
})
