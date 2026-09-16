// blood_sand_trail.js — nội dung quest "blood_sand_trail" (Điều 27, GAMEPLAY.md §9.6),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp NHẬN RA, khai theo STORY.md §8 quy tắc 1.
//
// =============================================================================================
// CHỖ CÁI TÊN "HUYẾT SA" RƠI XUỐNG LẦN THỨ HAI
// =============================================================================================
// Quest 8 (`abandoned_cave`) là lần đầu người chơi thấy dấu **hút tu vi** và nghe cái tên Huyết Sa.
// Quest 9 đóng lại bằng một hộ pháp tự tan tu vi. Quest trước (`dead_vein_intruders`) chỉ nói "có
// người lạ".
//
// Ở đây hai thứ đó chập vào nhau: kẻ lạ ngoài mạch chết để lại ĐÚNG cái dấu mà người chơi từng
// thấy trong hang. Không phải suy đoán của NPC — người chơi tự nhìn thấy, rồi mang về cho Tô Vân
// Nhi, người duy nhất đã đứng trong hang đó cùng mình.
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2) — được phép gọi tên Huyết Sa (arc 2 đã gọi rồi), nhưng KHÔNG:
//   · vì sao chúng cần tu vi, hay chúng định mở cái gì (§4.3 — arc 6);
//   · có nội gián cấp trưởng lão trong tông (arc 3 twist, danh tính tới arc 5);
//   · và Tô Vân Nhi ở đây LO THẬT. Viết cô thành kẻ đang diễn là hỏng cú twist arc 5 — đúng cảnh
//     báo mà quest 9 đã ghi.
//
// =============================================================================================
// HAI BƯỚC CÓ THỨ TỰ
// =============================================================================================
//   1. `monster_killed` blood_sand_acolyte × 2 — lục lại chỗ chúng đứng, và thấy cái dấu
//   2. `npc_talked` senior_sister              — mang về cho người đã ở trong hang cùng mình
//
// Thứ tự sống trong SCRIPT bằng điều kiện cộng dồn (`progress.felled >= 2` gác bước 2), cùng khuôn
// quest 7/8/9 — Java vẫn không biết khái niệm "bước" (GAMEPLAY §9.6).
//
// ⚠️ Quest này NHẬN TỪ CHU HOÀNH nên bước 2 mở lời với TÔ VÂN NHI, không phải với ông. Bài học
// quest 7: sự kiện `npc_talked` của chính lần mở lời để NHẬN quest bắn ra TRƯỚC khi quest tồn tại,
// nên một bước "nói chuyện với người giao quest" luôn đòi người chơi quay lại lần hai mà không có
// gì trên màn hình giải thích vì sao.
//
// ⚠️ `npc_talked` LẶP LẠI mỗi lần mở lời ⇒ bước 2 là CỜ, không phải bộ đếm.
//
// =============================================================================================
// 4.000 EXP
// =============================================================================================
// Hai lần hạ ở x=120/134 cộng một lượt về tông (x≈2 chỗ Tô Vân Nhi đứng) — tức trọn một vòng
// ~290m. Nhiều hơn quest trước đúng một bước trên đường cong (3.600 → 4.000), và cái thêm vào là
// quãng đường về chứ không phải thêm trận.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        // Bước 1 — không có điều kiện đứng trước: đây là bước mở.
        if (event.type === "monster_killed" && event.monster_code === "blood_sand_acolyte") {
            progress.felled = progress.felled + 1;
        }

        // Bước 2 — chỉ tính SAU khi đã lục đủ hai chỗ. Mở lời với cô trước đó (kể cả lần nhận
        // quest 8 hay lần nộp báo cáo quest 9) không đóng bước này.
        if (event.type === "npc_talked"
                && event.npc_code === "senior_sister"
                && progress.felled >= 2
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(4000);
    }
})
