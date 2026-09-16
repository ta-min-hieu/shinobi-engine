// dead_vein_intruders.js — nội dung quest "dead_vein_intruders" (Điều 27, GAMEPLAY.md §9.3),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp MỞ của quãng Lv22–27, khai theo STORY.md §8 quy tắc 1.
//
// =============================================================================================
// NHẶT LẠI SỢI CHỈ MÀ CAO TRÀO ARC 2 CỐ Ý BỎ LẠI
// =============================================================================================
// Quest 9 (`confront_the_steward`) đóng cao trào bằng một cái kết dang dở, đúng nguyên văn §6:
// *"đánh bại, nhưng hắn kịp tự tan tu vi, manh mối đứt"*. Hộ pháp chết, nhưng KHÔNG ai biết hắn
// đang đào dở cái gì ở vùng mạch chết phía đông — và giáo phái thì biết.
//
// Quest này chỉ nói đúng một điều: có người lạ mặc đồ lạ xuất hiện ở chỗ không ai tới, và họ không
// phải tán tu đi cướp. Người chơi ra xem, và bị đánh.
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2) — quest này KHÔNG được chạm:
//   · vết nứt Thiên Môn / chân tướng Đại Kiếp (arc 3 gieo nghi, arc 6 mới lộ);
//   · Sa Y Nhân, giáo chủ, hay mục đích thật của Huyết Sa (§4.3 có, nhưng đó là arc 6);
//   · và tuyệt đối không một chữ nào về Tô Vân Nhi (twist arc 5).
// Chu Hoành ở đây KHÔNG biết chúng là Huyết Sa. Ông chỉ biết "có người lạ", và đó là tất cả những
// gì lời thoại được phép nói — cái tên rơi xuống ở quest sau, khi người chơi nhìn thấy dấu vết.
//
// =============================================================================================
// MỘT BƯỚC, BA LẦN — CÙNG KHUÔN QUEST 10
// =============================================================================================
// Ba lần hạ `blood_sand_acolyte`. Một loại mục tiêu duy nhất, nên `on_accept` khai đúng một bộ đếm
// và `on_event` chỉ có một nhánh — cùng hình dạng `spirit_vein_raiders` (quest 10), và cố ý như
// thế: đây là nhịp MỞ của một quãng mới, chỗ để người chơi làm quen với loài mới và với quãng
// đường mới, không phải chỗ khoe cơ chế.
//
// ⚠️ `event.type` phải kiểm TRƯỚC: mỗi quest đang làm nhận MỌI loại sự kiện, nên thiếu nó thì
// `event.monster_code` là `undefined` trong sự kiện `npc_talked` và ngược lại.
//
// =============================================================================================
// 3.600 EXP — VÀ NÓ MUA BẰNG QUÃNG ĐƯỜNG
// =============================================================================================
// Nhịp cũ tăng dần: 500 → 800 → 1.200 → 1.600 → 1.800 → 2.000 → 2.800 → 3.300 → 3.700. 3.600 nằm
// đúng trên đường cong đó, không phải một bước nhảy.
//
// Cái giá thật nằm ở quãng đường: tế đồ đứng ở **x = 120 và 134**, trong khi sào huyệt đầu lĩnh
// của quest 12 mới ở x=93 — xa hơn ~1,4 lần, và đường đi phải qua TOÀN BỘ trục cũ (tán tu @21/@34,
// đoạt mạch @47, thám tử @66, quỷ đá @106). Ba lần hạ nghĩa là ít nhất hai lượt đi về.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "blood_sand_acolyte") {
            progress.felled = progress.felled + 1;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.felled >= 3) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(3600);
    }
})
