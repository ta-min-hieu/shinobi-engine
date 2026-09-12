// missing_disciple_trail.js — nội dung quest "missing_disciple_trail" (Điều 27, GAMEPLAY.md §9.6),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp ĐIỀU TRA, khai theo STORY.md §8 quy tắc 1.
// Nhịp hé lộ (§8 quy tắc 2): ở đây mới chỉ có "Hình Đường kết luận bỏ trốn, mà chưa ai tin".
// KHÔNG một chữ nào về hang luyện công, dấu vết hút tu vi, hay Huyết Sa — đó là nhịp TWIST sau.
//
// =============================================================================================
// QUEST NHIỀU BƯỚC CÓ THỨ TỰ — LẦN ĐẦU CỦA DỰ ÁN
// =============================================================================================
// Sáu quest trước mỗi quest chỉ có MỘT loại mục tiêu. Quest này có ba bước, và chúng phải xảy ra
// đúng thứ tự:
//
//   1. hỏi Chu Hoành          (`npc_talked` town_elder)
//   2. dẹp 2 tán tu chặn đường (`monster_killed` wandering_bandit)
//   3. báo lại Tô Vân Nhi      (`npc_talked` senior_sister)
//
// **Thứ tự sống trong SCRIPT, không sống trong Java.** `ProcessQuestEventUseCase` không biết khái
// niệm "bước" và không cần biết: nó chạy hook rồi ghi lại chuỗi tiến độ script trả về. Vì vậy quest
// nhiều bước không tốn một dòng Java nào — Điều 27 áp lần thứ sáu liên tiếp.
//
// Cách thi hành thứ tự là các ĐIỀU KIỆN CỘNG DỒN dưới đây (`progress.asked` gác bước 2,
// `progress.cleared >= 2` gác bước 3). Viết ba nhánh độc lập không có điều kiện cộng dồn thì người
// chơi có thể giết tán tu TRƯỚC khi hỏi ai, rồi quest vẫn xong — tức là ba bước rời rạc chứ không
// phải một cuộc điều tra.
//
// ⚠️ Ba cái bẫy của kiểu quest này, mỗi cái đã trả giá ở quest trước:
//   · `event.type` PHẢI kiểm trước — mỗi quest đang làm nhận MỌI loại sự kiện, nên thiếu nó thì
//     `event.npc_code` là `undefined` trong sự kiện quái chết và ngược lại.
//   · `npc_talked` LẶP LẠI mỗi lần mở lời ⇒ bước 1 và bước 3 là CỜ, không phải bộ đếm.
//   · Bước 3 hỏi ĐÚNG người giao quest (Tô Vân Nhi). Lần mở lời để NHẬN quest không tự đóng bước 3:
//     sự kiện đó bắn lúc mở lời, tức TRƯỚC khi quest tồn tại — và kể cả nếu không, `cleared >= 2`
//     vẫn chưa đúng.
//
// =============================================================================================
// 1.300 EXP — chọn từ quãng đường, không chọn rồi mới đi kiểm
// =============================================================================================
// quest 5 (đi 2m trong vùng an toàn, 0 trận) = 200; quest 6 (tới x=34, 2 trận) = 1.000; quest này
// đi x=2 → x=-5 → x=21/34 → x=2, khoảng 70m, cũng 2 trận tán tu ⇒ 1.300.
// Đường tối thiểu: 5.855 + 2 × 35 + 1.300 = 7.225 ⇒ đóng quest ở **Cấp 12** (Cấp 13 cần 7.800).
//
// KHÔNG thưởng vật phẩm: quest 6 vừa lấy đi hai thẻ bài và không trả lại gì, nên phần thưởng ở đây
// giữ nguyên một loại (EXP) cho người chơi đọc được tương quan giữa hai quest liền nhau.
// =============================================================================================
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ asked: false, cleared: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        // Bước 1 — hỏi Chu Hoành. Không có điều kiện đứng trước: đây là bước mở.
        if (event.type === "npc_talked" && event.npc_code === "town_elder" && !progress.asked) {
            progress.asked = true;
        }

        // Bước 2 — chỉ tính SAU khi đã hỏi. Tán tu giết trước lúc hỏi không được tính lùi.
        if (event.type === "monster_killed" && event.monster_code === "wandering_bandit" && progress.asked) {
            progress.cleared = (progress.cleared || 0) + 1;
        }

        // Bước 3 — chỉ tính khi hai bước trước đã xong.
        if (event.type === "npc_talked"
                && event.npc_code === "senior_sister"
                && progress.asked
                && progress.cleared >= 2
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(1300);
    }
})
