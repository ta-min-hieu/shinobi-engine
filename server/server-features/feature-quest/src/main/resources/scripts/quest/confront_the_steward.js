// confront_the_steward.js — nội dung quest "confront_the_steward" (Điều 27, GAMEPLAY.md §9.6),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-2 §6 (Ngoại Môn) — **CAO TRÀO**, khai theo STORY.md §8 quy tắc 1.
//
// =============================================================================================
// ĐÂY LÀ CHỖ ARC 2 ĐÓNG, VÀ NÓ ĐÓNG BẰNG MỘT CÁI KẾT DANG DỞ
// =============================================================================================
// §6 arc 2 viết nguyên văn: *"hộ pháp Huyết Sa cải trang làm chấp sự tông môn — kẻ đứng sau các vụ
// mất tích. Đánh bại, nhưng hắn kịp tự tan tu vi, manh mối đứt."*
//
// Bốn quest trước đã đi đúng nhịp tới đây:
//   quest 6  gieo mầm   — một thẻ bài mang tên đứa không về
//   quest 7  điều tra   — Hình Đường kết "bỏ trốn", Chu Hoành không tin
//   quest 8  twist      — hang bỏ hoang, dấu hút tu vi, cái tên Huyết Sa rơi xuống
//   quest 9  CAO TRÀO   — cái lệnh phù ấy chỉ về đúng người đã đóng hồ sơ
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2) — quest này ĐƯỢC nói ra thân phận hộ pháp cải trang, vì đó CHÍNH LÀ
// cao trào arc 2. Nó vẫn KHÔNG được chạm tới:
//   · chân tướng Đại Kiếp / vết nứt Thiên Môn — arc 3 mới gieo nghi, arc 6 mới lộ;
//   · chuyện trong tông có nội gián cấp trưởng lão — arc 3 twist, danh tính giữ tới arc 5;
//   · và tuyệt đối không một chữ nào về việc Tô Vân Nhi là tai mắt bị ép buộc (arc 5).
// Ở đây cô báo tin và nhẹ cả người THẬT LÒNG. Viết cô thành kẻ đang diễn là hỏng cú twist arc 5.
//
// =============================================================================================
// HAI BƯỚC CÓ THỨ TỰ
// =============================================================================================
//   1. `monster_killed` blood_sand_enforcer — đối chất, và hắn trở mặt (x=-12, V41)
//   2. `npc_talked` senior_sister           — báo lại: hắn tự tan tu vi, manh mối đứt
//
// Thứ tự sống trong SCRIPT bằng điều kiện cộng dồn (`progress.felled` gác bước 2), cùng khuôn
// quest 7 và quest 8 — Java vẫn không biết khái niệm "bước" (GAMEPLAY §9.6). Đây là lần thứ BẢY
// liên tiếp thêm nội dung quest mà không sửa một dòng Java nội dung nào.
//
// ⚠️ Quest này NHẬN TỪ CHU HOÀNH, và đó là lý do bước 1 KHÔNG phải "hỏi Chu Hoành". Quest 7 đã trả
// giá cho bài học đó: sự kiện `npc_talked` của chính lần mở lời để NHẬN quest bắn ra TRƯỚC khi
// quest tồn tại, nên một bước "nói chuyện với người giao quest" luôn đòi người chơi quay lại lần
// hai mà không có gì trên màn hình nói vì sao. Người giao là ông, nên bước đầu là việc phải đi làm.
//
// ⚠️ `event.type` phải kiểm TRƯỚC ở cả hai nhánh: mỗi quest đang làm nhận MỌI loại sự kiện, nên
// thiếu nó thì `event.npc_code` là `undefined` trong sự kiện quái chết và ngược lại.
//
// ⚠️ `npc_talked` LẶP LẠI mỗi lần mở lời ⇒ bước 2 là CỜ, không phải bộ đếm.
//
// =============================================================================================
// 2.000 EXP — lớn nhất từ trước tới nay, vì nó đóng cả một arc
// =============================================================================================
//   quest 6  tới x=34, 2 trận tán tu                              = 1.000
//   quest 7  ~70m, 2 trận tán tu                                  = 1.300
//   quest 8  ~61m MỘT CHIỀU + một trận thắng ở nhát cuối cùng      = 1.600
//   quest 9  ĐI RẤT GẦN (x=0 → -12, trong vùng an toàn) nhưng là
//            trận khó nhất của arc: 150 máu, atk 30, và HAI PHA    = 2.000
//
// ⚠️ Phần thưởng ở đây KHÔNG mua bằng quãng đường — đó là chỗ nó khác ba quest trước. Hắn đứng
// TRONG tông môn (vùng an toàn `sect_grounds` [-15, 5], V35) nên người chơi tới nơi với máu đầy và
// không mất một giọt nào dọc đường. Cái giá nằm trọn trong trận đấu.
//
// Đường chơi TỐI THIỂU: 8.895 (hết quest 8) + 300 (chính hắn) + 2.000 = **11.195**.
// `ExperienceCurve` tuyến tính 100 × cấp ⇒ tổng của Cấp 15 là 10.500, của Cấp 16 là 12.000
// ⇒ đóng quest ở **Cấp 15**. Cao trào đẩy HAI cấp (13 → 15) trong khi mỗi quest thường đẩy một —
// đó là chủ đích: arc đóng thì phải thấy được bằng con số, không chỉ bằng lời thoại.
//
// KHÔNG thưởng vật phẩm: §6 nói thẳng "manh mối ĐỨT". Cho người chơi cầm về một vật chứng thứ hai
// là mâu thuẫn trực tiếp với câu đó — hắn tự tan tu vi chính là để không còn gì mà nhặt.
// =============================================================================================
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: false, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        // Bước 1 — đối chất. Không có điều kiện đứng trước: đây là bước mở.
        if (event.type === "monster_killed" && event.monster_code === "blood_sand_enforcer") {
            progress.felled = true;
        }

        // Bước 2 — chỉ tính SAU khi đã hạ được hắn. Mở lời với Tô Vân Nhi trước đó (kể cả lần nhận
        // quest 8 hay lần nộp lệnh phù) không đóng bước này.
        if (event.type === "npc_talked"
                && event.npc_code === "senior_sister"
                && progress.felled
                && !progress.reported) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(2000);
    }
})
