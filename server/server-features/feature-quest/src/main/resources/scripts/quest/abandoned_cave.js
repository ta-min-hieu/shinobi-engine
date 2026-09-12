// abandoned_cave.js — nội dung quest "abandoned_cave" (Điều 27, GAMEPLAY.md §9.5/§9.6), KHÔNG
// hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp TWIST. Khai theo STORY.md §8 quy tắc 1.
//
// =============================================================================================
// ĐÂY LÀ CHỖ CÁI TÊN "HUYẾT SA" LẦN ĐẦU XUẤT HIỆN TRONG GAME
// =============================================================================================
// §6 arc 2 viết: "người chơi và Tô Vân Nhi lần ra một hang luyện công bỏ hoang, phát hiện dấu vết
// HÚT TU VI — cái tên Huyết Sa lần đầu xuất hiện". Ba quest trước đã đi đúng nhịp đó: quest 6 gieo
// mầm (một thẻ bài của đứa không về), quest 7 điều tra (Hình Đường kết luận bỏ trốn, Chu Hoành
// không tin). Quest này là chỗ cái tên rơi xuống.
//
// Nó rơi xuống qua HAI đường, cả hai đều là thứ người chơi TỰ THẤY chứ không phải ai đó kể:
//   · tên con canh cửa hang hiện trên khung mục tiêu — "Thám Tử Huyết Sa";
//   · vật nó bỏ lại — "Huyết Sa Lệnh Phù" — nằm trong túi người chơi.
//
// ⚠️ VÀ CHỈ ĐẾN THẾ. Nhịp hé lộ (§8 quy tắc 2) cho phép cái TÊN, không cho phép:
//   · chân tướng Đại Kiếp hay vết nứt Thiên Môn (arc 3 mới gieo nghi, arc 6 mới lộ);
//   · thân phận hộ pháp cải trang làm chấp sự (cao trào arc 2 — quest sau);
//   · và tuyệt đối không một câu nào chạm tới việc Tô Vân Nhi là tai mắt bị ép buộc (arc 5).
// Cô ở đây LO THẬT và giận thật. Viết cô thành người đang giấu là hỏng cú twist arc 5.
//
// =============================================================================================
// HAI BƯỚC, VÀ BƯỚC HAI LÀ "MANG VẬT TỚI NPC"
// =============================================================================================
//   1. `monster_killed` blood_sand_scout — hạ kẻ canh cửa hang (x=66, V40)
//   2. `npc_talked` senior_sister + `ctx.take_item` — đưa tận tay Tô Vân Nhi cái lệnh phù
//
// Thứ tự sống trong script bằng điều kiện cộng dồn (`slain >= 1` gác bước 2), cùng khuôn quest 7 —
// Java vẫn không biết khái niệm "bước" (GAMEPLAY §9.6). Lấy đồ là ĐIỀU KIỆN của cả lần chạy hook:
// tiến độ mới và lần lấy đồ nằm trong CÙNG một transaction, thiếu lệnh phù thì lần mở lời đó không
// để lại dấu vết gì (GAMEPLAY §9.5).
//
// ⚠️ `!progress.delivered` trong điều kiện là thứ ngăn lần mở lời THỨ HAI đòi thêm một lệnh phù nữa.
//
// =============================================================================================
// 1.600 EXP — quãng đường dài nhất từ trước tới nay, và trận sát nút nhất
// =============================================================================================
//   quest 6  tới x=34, 2 trận tán tu                       = 1.000
//   quest 7  x=2 → −5 → 21/34 → 2 (~70m), 2 trận tán tu     = 1.300
//   quest 8  vùng an toàn → x=66 (~61m MỘT CHIỀU) + quay về,
//            đi ngang tán tu @21/@34 (quỷ đá đã dời ra 80,
//            SAU hang — xem V40 mục 6, playtest bác bản đầu),
//            rồi một trận thắng ĐÚNG Ở NHÁT CUỐI CÙNG      = 1.600
//
// ⚠️ Con số của trận đó chốt sau BA lượt playtest (máu quái 150 → 120 → 100), vì hai thứ không
// nằm trong công thức: quãng đường tốn máu, và việc quái ra đòn TRƯỚC hai nhát (aggro 6m > tầm
// đánh 2m ⇒ người chơi băng 4m dưới làn đạn, mất 40 máu). Xem V40 mục CÂN BẰNG.
//
// Đường chơi TỐI THIỂU: 7.225 (hết quest 7) + 70 (con canh cửa) + 1.600 = **8.895**.
// `ExperienceCurve` tuyến tính 100 × cấp ⇒ tổng của Cấp 13 là 7.800, của Cấp 14 là 9.100
// ⇒ đóng quest ở **Cấp 13**. Vẫn đúng nhịp "mỗi quest arc 2 đẩy một cấp".
//
// KHÔNG thưởng vật phẩm: quest này LẤY ĐI lệnh phù (vật chứng phải nằm trong tay Tô Vân Nhi, không
// phải trong túi người chơi), nên phần thưởng giữ một loại để tương quan với hai quest trước đọc
// được thẳng bằng con số.
// =============================================================================================
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ slain: 0, delivered: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "blood_sand_scout") {
            progress.slain = (progress.slain || 0) + 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "senior_sister"
                && progress.slain >= 1
                && !progress.delivered) {
            progress.delivered = true;
            ctx.take_item("blood_sand_talisman", 1);
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.delivered) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(1600);
    }
})
