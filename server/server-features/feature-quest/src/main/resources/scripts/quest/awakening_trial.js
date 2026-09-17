// awakening_trial.js — nội dung quest "awakening_trial" (Điều 27, GAMEPLAY.md §9.3), KHÔNG hardcode
// Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-1 §6 (Tạp Dịch) — nhịp ĐÓNG của arc, bắt buộc khai theo STORY.md §8 quy tắc 1.
// Nguyên văn §6 arc 1: "Đánh đuổi được hắn → Chu Hoành để ý → được đặc cách dự khảo hạch → thức
// tỉnh linh căn (Lv10)", và "Mở khoá: chọn hệ (Lv10)".
//
// ⚠️ NHỊP HÉ LỘ (STORY.md §8 quy tắc 2): arc 1 chỉ được GIEO. Kỳ khảo hạch là chuyện tông môn
// thuần tuý — không một câu nào ở đây hay ở town_elder.js được nhắc tới mảnh ngọc cổ người chơi
// vừa nhặt ở quest 3, tới Thiên Môn, hay tới Huyết Sa. Linh căn người chơi thức tỉnh vì họ đã
// trụ được, không vì di vật nào cả; mảnh ngọc chỉ phát sáng ở arc 3.
//
// ⚠️ ĐÂY LÀ QUEST THỨ TƯ, và là quest thứ BA liên tiếp không tốn một dòng Java sản phẩm nào
// (V28 → V30 → V33). Java vẫn KHÔNG biết quest này quan tâm con quái nào; dòng `event.monster_code`
// dưới đây là nơi DUY NHẤT biết điều đó.
//
// ⚠️ Mã quái `trial_construct` là KHOÁ NGHIỆP VỤ có dòng DB thật trỏ vào (`monster_template.code`,
// seed V33) — ADR-038 D5 cấm đổi. Tên hiển thị tu tiên ("Trắc Linh Thạch Nhân") nằm ở bundle i18n.
//
// =============================================================================================
// HAI CON SỐ DƯỚI ĐÂY, VÀ VÌ SAO CHÚNG KHÔNG PHẢI LÀ SỞ THÍCH
// =============================================================================================
// 1. MỘT trận, không phải hai. Ba quest trước đều theo lối "giết N con"; quest này dừng ở N = 1 vì
//    hai lý do độc lập nhau đều dẫn tới cùng một số:
//      · TƯỜNG THUẬT — khảo hạch là MỘT lượt trình diện, không phải một buổi cày.
//      · CƠ CHẾ — server-world KHÔNG có hồi máu ngoài chiến đấu, nên hai trận liên tiếp ở arc 1 là
//        chết. Quest 3 đang mắc đúng chuyện đó (hạ con thạch vệ thứ nhất xong còn 38/118 mà vẫn
//        cần con thứ hai). Lỗ hổng hệ thống đó đã báo cáo riêng cho chủ dự án — quest 4 không giả
//        vờ nó không tồn tại, nó chỉ không bước vào.
//
// 2. 1800 EXP — con số CHỌN TỪ RÀNG BUỘC, không phải chọn rồi mới đi kiểm. Ràng buộc: lĩnh thưởng
//    quest này XONG thì người chơi PHẢI ở Cấp 10, vì Cấp 10 là chỗ `SelectFactionUseCase.MIN_LEVEL`
//    mở khoá chọn hệ phái — thứ lời thoại của Chu Hoành hứa. Đường chơi TỐI THIỂU (chỉ EXP quest +
//    đúng số quái mà chính các quest bắt giết, không cày thêm con nào):
//
//      quest 1  500 + 3 × 20 (mộc nhân)      =   560
//      quest 2  800 + 1 × 35 (tán tu)        =   835   → cộng dồn 1.395
//      quest 3 1200 + 2 × 55 (thạch vệ)      = 1.310   → cộng dồn 2.705
//      quest 4      + 1 × 80 (cỗ khảo hạch)  =    80   → cộng dồn 2.785
//      ⇒ phần thưởng phải ≥ 4.500 − 2.785 = 1.715. Lấy 1.800 cho tròn và có biên.
//
//    `ExperienceCurve` là tuyến tính 100 × cấp ⇒ tổng EXP của Cấp 10 là 4.500, của Cấp 11 là 5.500.
//    Đường tối thiểu về đích ở 4.585: đúng Cấp 10, chưa chạm Cấp 11. Phép tính này được KHOÁ LẠI
//    bằng `QuestIntegrationTest.shouldLandExactlyAtLevelTen_afterTheWholeArcOneChain` — nó đối
//    chiếu với `ExperienceCurve` thật
//    chứ không chép lại công thức, nên đổi đường cong EXP là test đỏ ngay.
//
//    ⚠️ Đổi hai số này là quyết định gameplay riêng, cần playtest riêng — và nếu đổi số 1800 thì
//    phải đọc lại ràng buộc Cấp 10 ở trên, đừng đổi mình nó.
// =============================================================================================
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ endured: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "trial_construct") {
            progress.endured = (progress.endured || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.endured >= 1) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(1800);
    }
})
