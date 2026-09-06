// clear_old_archive.js — nội dung quest "clear_old_archive" (Điều 27, GAMEPLAY.md §9.3),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-1 §6 (Tạp Dịch) — nhịp GIEO MẦM của arc, bắt buộc khai theo STORY.md §8 quy tắc 1.
// Nguyên văn §6 arc 1: "khi dọn dẹp một góc Tàng Thư Đường bỏ hoang, người chơi nhặt được mảnh
// ngọc cổ không ai thèm lấy (di vật Thượng Cổ — McGuffin xuyên truyện, arc này chỉ là 'vật may
// mắn')."
//
// ⚠️ NHỊP HÉ LỘ (STORY.md §8 quy tắc 2): arc 1 chỉ được GIEO. Chu Hoành sai người chơi đi DỌN, ông
// không biết có mảnh ngọc và không được nhắc tới nó — xem town_elder.js, nhánh `old_archive`. Mảnh
// ngọc chỉ là một dòng phần thưởng ở đây; nó phát sáng ở arc 3, lộ thân phận ở arc 5.
//
// ⚠️ ĐÂY LÀ QUEST THỨ BA và là quest thứ ba THEO DÕI GIẾT QUÁI. Nó không phải sửa MỘT DÒNG JAVA
// nào — đó là bằng chứng đợt gỡ hardcode 2026-09-05 (`ProcessMonsterKilledUseCase` tổng quát hoá
// tra cứu quest) đã trả đúng thứ nó hứa. Java KHÔNG biết quest này quan tâm con quái nào; dòng
// `event.monster_code` dưới đây là nơi DUY NHẤT biết điều đó.
//
// ⚠️ Mã quái `archive_warden` và mã vật phẩm `ancient_jade_shard` là KHOÁ NGHIỆP VỤ có dòng DB
// thật trỏ vào (`monster_template.code`, `item_template.code`, seed V30) — ADR-038 D5 cấm đổi. Tên
// hiển thị tu tiên ("Thạch vệ Tàng Thư", "Mảnh Ngọc Cổ") nằm ở bundle i18n, không nằm ở đây.
//
// ⚠️ BA CON SỐ DƯỚI ĐÂY LÀ CÂN BẰNG, KHÔNG PHẢI TƯỜNG THUẬT (2 lần dọn, 1200 EXP, 1 mảnh ngọc).
// Chọn theo tương quan với hai quest trước: 3 cọc gỗ đứng yên = 500 EXP; đuổi 1 tán tu biết đánh
// trả = 800 EXP; hạ 2 thạch vệ (mỗi con dai gấp rưỡi tán tu, xem bảng ở V30 §2) = 1200 EXP. Cộng
// dồn cả ba quest cùng EXP quái là người chơi tới quanh Cấp 7 — đúng đà đi tới mốc Lv10 "thức tỉnh
// linh căn" mà STORY.md §6 đặt làm điểm đóng arc 1. Đổi ba số này là quyết định gameplay riêng,
// cần playtest riêng.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ cleared: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "archive_warden") {
            progress.cleared = (progress.cleared || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.cleared >= 2) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(1200);
        ctx.give_item("ancient_jade_shard", 1);
    }
})
