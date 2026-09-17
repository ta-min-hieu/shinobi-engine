// foundation_pill_run.js — nội dung quest "foundation_pill_run" (Điều 27, GAMEPLAY.md §9.6).
//
// STORY: arc-2 §6 — chuẩn bị cho Trúc Cơ. Đan dược là thứ người chơi phải TỰ gom, đúng mạch "tài
// nguyên phân phát theo thứ hạng" của nhịp trước.
//
// =============================================================================================
// MỤC TIÊU LOẠI BA — GIAO ĐỒ CHO NPC
// =============================================================================================
// Dùng `ctx.take_item`, cơ chế đã dựng 2026-09-11. Đan dược tới từ chính bảng rơi đồ của
// `wasteland_beast` (V48 cho 50% × 1–2 viên), nên người chơi vừa đánh vừa gom được — không phải
// đi mua.
//
// ⚠️ `take_item` CHỈ gọi được trong `on_event`. Gọi ở `on_accept`/`on_claim` là lỗi script (ồn),
// không phải lời đòi đồ bị bỏ qua (im).
// ⚠️ Thiếu món ⇒ hook rollback TOÀN BỘ, không lấy một nửa — luật 1 của mục tiêu loại ba.
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ felled: 0, delivered: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);

        if (event.type === "monster_killed" && event.monster_code === "wasteland_beast") {
            progress.felled = progress.felled + 1;
        }

        if (event.type === "npc_talked"
                && event.npc_code === "town_elder"
                && progress.felled >= 2
                && !progress.delivered) {
            ctx.take_item("minor_healing_pill", 3);
            progress.delivered = true;
        }

        ctx.save_progress(JSON.stringify(progress));
        if (progress.delivered) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(4800);
    }
})
