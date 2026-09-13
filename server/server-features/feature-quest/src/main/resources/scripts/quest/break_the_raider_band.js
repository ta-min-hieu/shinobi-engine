// break_the_raider_band.js — nội dung quest "break_the_raider_band" (Điều 27, GAMEPLAY.md §9.3),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java).
//
// STORY: arc-1 §6 (Tạp Dịch) — quest THỨ MƯỜI HAI, nhịp ĐÓNG của quãng Lv10–18 và của cả arc 1.
// Lĩnh thưởng xong người chơi ở ĐÚNG Cấp 18, tức đúng dải Lv1–18 mà STORY khai cho arc 1 —
// lần đầu tiên kể từ khi tài liệu được duyệt (2026-09-05).
//
// =============================================================================================
// HAI BƯỚC, và bước cuối là MỞ LỜI với người đã giao việc
// =============================================================================================
//   1. `monster_killed` raider_chieftain — hạ đầu lĩnh ở sào huyệt x=93
//   2. `npc_talked` town_elder           — về báo, và arc 1 đóng
//
// Cùng khuôn quest 11: nhận từ Chu Hoành nên bước đầu phải là việc đi làm, không phải mở lời (sự
// kiện `npc_talked` bắn ra TRƯỚC khi quest tồn tại — bài học của quest 7).
//
// =============================================================================================
// VÌ SAO TRẬN NÀY CẦN BỘ ĐỒ CỦA QUEST 11 — số, không phải nhịp kể chuyện
// =============================================================================================
// `RaiderBandBalanceTest` (server-world) đo cả hai chiều, trên chỉ số THẬT của V45:
//
//   * mặc bộ "Ngoại Môn": tới nơi còn 111 máu ⇒ **4 nhát so với 6**, biên 2 — thắng chắc nếu đánh
//     sạch, thua nếu hụt ba nhát liên tiếp (người chơi trúng 91%);
//   * mặc bộ "Tạp Dịch": THUA, và thua kể cả khi tính trên MÁU ĐẦY — cận trên rộng rãi nhất.
//
// ⇒ Phần thưởng quest 11 không phải một dòng chữ, nó là ĐIỀU KIỆN CẦN của quest 12. Đó là lý do
// hai quest này phải đi liền nhau bằng `prerequisite_quest_id`, không phải vì thứ tự kể chuyện.
//
// =============================================================================================
// QUÃNG ĐƯỜNG ĐÃ ĐƯỢC DỌN, và đó là một quyết định chứ không phải may mắn
// =============================================================================================
// Đường đông tới x=93 đi qua tán tu @21, @34, đoạt mạch @47, thám tử @66 — mỗi con một nhát. Với
// giáp 50 của bộ Ngoại Môn, tổng cộng 59 máu (15+15+12+17), tới nơi còn 111/170.
//
// Quỷ đá @80 KHÔNG còn trên đường: V45 dời nó 80 → 106, đúng việc V40 đã làm một lần rồi khi dời
// nó 47 → 80 (một mình nó lấy 31 máu mỗi lượt đi ngang, đủ để biến trận cuối thành thua chắc).
// Sau khi dời, x≈80 thành khe YÊN TĨNH — cách thám tử 14m và cách sào huyệt 13m, ngoài tầm aggro
// của cả hai — nên người chơi đứng đó hồi đầy máu trước trận (ADR-046) nếu muốn.
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2): đây vẫn là arc 1. Đám đoạt mạch là cướp thường, KHÔNG phải Huyết
// Sa, và Chu Hoành không được úp mở gì về một thế lực lớn hơn. Cái tên Huyết Sa lần đầu xuất hiện
// ở quest 8 của arc 2 (`abandoned_cave`), và nhịp đó phải giữ nguyên.
//
// 3.700 EXP — xem bảng đường chơi tối thiểu ở `spirit_vein_raiders.js`.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: false, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        // Tiến độ là CỜ, không phải bộ đếm: đầu lĩnh chỉ có MỘT, và `respawn_seconds` của hắn dài
        // (V45). Đếm thì một lần hồi sinh + hạ lại sẽ đẩy tiến độ quá mốc mà không ai nhận ra.
        if (event.type === "monster_killed" && event.monster_code === "raider_chieftain") {
            progress.felled = true;
        }

        // Bước 2 gác bằng `felled` — về báo trước khi hạ hắn thì lần mở lời đó không để lại gì.
        if (event.type === "npc_talked" && event.npc_code === "town_elder" && progress.felled) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(3700);
    }
})
