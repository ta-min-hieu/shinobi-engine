// spirit_vein_raiders.js — nội dung quest "spirit_vein_raiders" (Điều 27, GAMEPLAY.md §9.3), KHÔNG
// hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-1 §6 (Tạp Dịch) — quest THỨ MƯỜI, mở quãng Lv10–18 mà STORY khai nhưng chuỗi cũ chưa
// bao giờ chạm tới (bốn quest arc 1 đưa người chơi tới ĐÚNG Cấp 10 rồi dừng).
//
// Dòng lore đã có sẵn ở §7: "Tán tu cường đạo — tu sĩ không tông môn, sống bằng cướp ở rìa linh
// mạch", chéo với "5 Linh Mạch — yêu thú tụ theo thuộc tính". Con `vein_raider` là bản một bậc trên
// `wandering_bandit`: không cướp dọc đường nữa mà CHIẾM hẳn một linh mạch sơ cấp của tông môn.
//
// ⚠️ NHỊP HÉ LỘ (STORY.md §8 quy tắc 2): vẫn là arc 1, vẫn chỉ được GIEO. Không một câu nào ở đây
// hay ở town_elder.js nhánh `spirit_vein` được nhắc tới Huyết Sa (arc 2 mới lộ), Thiên Môn hay
// chân tướng Đại Kiếp (arc 3/5). Đám này là cướp thường, và tông môn xử lý chúng như việc nội bộ.
//
// =============================================================================================
// BA CON, VÀ VÌ SAO KHÔNG PHẢI MỘT
// =============================================================================================
// Quest 4 (khảo hạch) dừng ở MỘT trận vì lúc đó server-world chưa có hồi máu ngoài chiến đấu, nên
// hai trận liên tiếp là chết. Điều kiện đó đã hết hiệu lực: ADR-046 D2 dựng `HealthRegenSystem`
// (8 giây ngoài chiến đấu → 3% máu tối đa mỗi giây). Ba con là con số đầu tiên trong arc 1 CÓ THỂ
// đòi, và nó đòi người chơi dùng đúng cơ chế vừa được trao.
//
// ⚠️ Nhưng ba con KHÔNG có nghĩa là ba trận liên tiếp không nghỉ: điểm spawn ở x=47 chỉ có MỘT, và
// `respawn_seconds` của loài này là 20 (V45). Người chơi hạ một con, chờ nó hồi sinh, hạ tiếp — và
// đúng 20 giây đó là thừa thời gian để `HealthRegenSystem` kéo máu về đầy (3% × 118 ≈ 3,5 máu/giây
// ⇒ 34 máu mất dọc đường hồi trong ~10 giây). Nhịp nghỉ nằm sẵn trong dữ liệu, không phải lời hứa.
//
// =============================================================================================
// 2.800 EXP — CHỌN TỪ RÀNG BUỘC, như 1.800 của quest 4
// =============================================================================================
// Đường chơi TỐI THIỂU của cả quãng Lv10–18 (chỉ EXP thưởng quest + đúng số quái mà chính các quest
// bắt hạ), bắt đầu từ 4.585 EXP mà arc 1 cũ để lại:
//
//   quest 10  2.800 + 3 × 140 (đoạt mạch)   = 3.220  → cộng dồn  7.805
//   quest 11  3.300 + 2 × 140 (đoạt mạch)   = 3.580  → cộng dồn 11.385
//   quest 12  3.700 + 1 × 260 (đầu lĩnh)    = 3.960  → cộng dồn 15.345
//
// `ExperienceCurve` tuyến tính 100 × cấp ⇒ tổng Cấp 18 là 15.300, Cấp 19 là 17.100. Đường tối
// thiểu về đích ở 15.345: ĐÚNG Cấp 18, chưa chạm Cấp 19 — tức là khép đúng dải Lv1–18 mà STORY.md
// §6 khai cho arc 1, lần đầu tiên kể từ khi tài liệu đó được duyệt.
//
// ⚠️ Phép tính này được KHOÁ bằng `QuestIntegrationTest` (server-database) — nó đối chiếu với
// `ExperienceCurve` thật và `monster_template.exp_reward` thật, không chép lại công thức. Đổi bất
// kỳ số nào ở đây mà không đổi ô test đó là test đỏ ngay, và đó là việc nó sinh ra để làm.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0 }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "monster_killed" && event.monster_code === "vein_raider") {
            progress.felled = (progress.felled || 0) + 1;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.felled >= 3) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(2800);
    }
})
