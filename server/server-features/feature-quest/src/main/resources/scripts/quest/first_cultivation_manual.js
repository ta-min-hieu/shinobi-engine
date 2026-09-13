// first_cultivation_manual.js — nội dung quest "first_cultivation_manual" (Điều 27, GAMEPLAY.md
// §9.3), KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java).
//
// STORY: arc-1 §6 (Tạp Dịch) — quest THỨ MƯỜI MỘT, và là chỗ hiện thực mốc duy nhất của arc 1 chưa
// từng được làm: **"Mở khoá: công pháp đầu tiên (Lv15)"**.
//
// =============================================================================================
// "CÔNG PHÁP" LÀ MỘT BỘ TRANG BỊ, KHÔNG PHẢI MỘT CƠ CHẾ MỞ KHOÁ CHIÊU — chủ dự án chốt 2026-09-13
// =============================================================================================
// Trong code KHÔNG có cơ chế mở khoá chiêu theo cấp nào cả: chỉ có `SelectFactionUseCase.MIN_LEVEL`
// (chọn hệ ở Cấp 10) và `item_template.required_level` (ADR-036). Dựng một cơ chế học chiêu mới là
// việc chạm SkillRegistry + có thể chạm protocol ⇒ cần ADR riêng (CLAUDE.md §4).
//
// Nên "công pháp" ở đây là bộ **"Ngoại Môn"** `required_level 15` (V45): pháp y, pháp khí và bùa
// hộ thân mà tông môn cấp cho đệ tử đã chứng minh được mình. Nó dùng ĐÚNG cơ chế đã chạy
// (ADR-036 — trang bị có chỉ số thật), và nó lấp luôn một lỗ khác đo được cùng ngày: trước V45,
// trang bị DỪNG ở `required_level 5` (bộ "Hạ Nhẫn"), nên cả quãng Lv10–18 không có gì để cày.
//
// ⚠️ SÁU món, cố ý không phải bảy: ô `ACCESSORY_2` để trống, đúng bằng lũt đồ mà mọi lớp cân bằng
// của dự án giả định (`SpiritVeinDuelBalanceTest` javadoc giải thích vì sao). Bộ được TRAO đúng
// bằng bộ được TÍNH — không để lại khe hở giữa hai thứ đó.
//
// =============================================================================================
// HAI BƯỚC CÓ THỨ TỰ, và bước đầu KHÔNG phải "hỏi Chu Hoành"
// =============================================================================================
//   1. `monster_killed` vein_raider × 2 — đòi lại mấy quyển công pháp đám đoạt mạch lấy của tông
//   2. `npc_talked` town_elder        — mang về, và ông lão trao lại cùng phần thưởng
//
// Quest này NHẬN TỪ CHU HOÀNH (`town_elder.js` nhánh `first_manual`), nên một bước "nói chuyện với
// Chu Hoành" không bao giờ đóng được bằng chính lần mở lời để nhận quest: sự kiện `npc_talked` bắn
// ra TRƯỚC khi quest tồn tại. Quest 7 đã trả giá cho bài học đó (`missing_disciple_trail.js`), và
// quest 9 viết lại nó thành luật. Nên bước đầu là việc phải đi làm, bước sau mới là mở lời.
//
// Thứ tự sống trong SCRIPT (`progress.recovered` gác bước 2), không sống trong Java (GAMEPLAY §9.6).
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2): vẫn arc 1. Ông lão trao công pháp vì người chơi đã đứng được ở
// linh mạch, KHÔNG phải vì mảnh ngọc cổ (quest 3) — mảnh ngọc chỉ phát sáng ở arc 3, và ông vẫn
// không biết nó tồn tại.
//
// 3.300 EXP — xem bảng đường chơi tối thiểu ở `spirit_vein_raiders.js`; ba quest của quãng này
// được tính CÙNG NHAU để về đích đúng Cấp 18.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0, recovered: false, reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "vein_raider" && !progress.recovered) {
            progress.felled = (progress.felled || 0) + 1;
            if (progress.felled >= 2) {
                progress.recovered = true;
            }
        }

        // Bước 2 chỉ mở khi bước 1 xong — `recovered` là cái chốt, và nó được kiểm TRƯỚC khi ghi
        // `reported`. Mở lời với ông lão lúc chưa đòi đủ thì lần đó không để lại dấu vết gì.
        if (event.type === "npc_talked" && event.npc_code === "town_elder" && progress.recovered) {
            progress.reported = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(3300);
        // Bộ "Ngoại Môn" — SÁU món, đúng bằng lũt đồ mà cân bằng giả định (xem đầu file).
        ctx.give_item("outer_sect_blade", 1);
        ctx.give_item("outer_sect_robe", 1);
        ctx.give_item("outer_sect_cap", 1);
        ctx.give_item("outer_sect_gloves", 1);
        ctx.give_item("outer_sect_boots", 1);
        ctx.give_item("outer_sect_talisman", 1);
    }
})
