// report_to_outer_sect.js — nội dung quest "report_to_outer_sect" (Điều 27, GAMEPLAY.md §9.3),
// KHÔNG hardcode Java. Mọi hiệu ứng đi qua ctx.* (callback Java) — hook KHÔNG trả giá trị.
//
// STORY: arc-2 §6 (Ngoại Môn) — nhịp MỞ của arc, bắt buộc khai theo STORY.md §8 quy tắc 1.
// Nguyên văn §6 arc 2: "Mục tiêu: đứng vững ở ngoại môn"; §5 Tô Vân Nhi: "Quest đồng hành, dạy hệ
// thống social". Quest này chỉ làm đúng một việc: đưa người chơi tới gặp cô lần đầu.
//
// =============================================================================================
// QUEST ĐẦU TIÊN CỦA DỰ ÁN KHÔNG PHẢI "GIẾT N CON"
// =============================================================================================
// Bốn quest trước đều cùng một khuôn: đếm `event.monster_code`. Quest này dùng LOẠI SỰ KIỆN thứ
// hai — `npc_talked` — nên nó là lần đầu `ProcessQuestEventUseCase` phải phân biệt hai loại sự
// kiện, và cũng chính là lý do lớp đó được tổng quát hoá TRƯỚC (2026-09-09), y như hồi quest 2 bắt
// nó bỏ hằng số mã quest hardcode. Cùng một luật, áp lần thứ hai: cơ chế đi trước nội dung.
//
// ⚠️ `event.type` PHẢI kiểm trước. Từ khi có hai loại sự kiện, mỗi quest đang làm nhận CẢ HAI —
// `event.npc_code` là `undefined` trong sự kiện quái chết, và `event.monster_code` là `undefined`
// trong sự kiện này. Không có `event.type` ở đầu điều kiện thì mọi so sánh phía sau là ngẫu nhiên.
//
// ⚠️ SỰ KIỆN NÀY LẶP LẠI. Người chơi nói chuyện với Tô Vân Nhi bao nhiêu lần thì bắn bấy nhiêu lần
// (mốc đặt ở lúc MỞ LỜI — xem javadoc QuestFacade.onNpcTalked). Vì thế tiến độ ở đây là một CỜ
// (`reported = true`), KHÔNG phải bộ đếm cộng dồn như bốn quest kia. Viết `+= 1` ở đây là quest tự
// đếm số lần chào hỏi, và nếu mục tiêu là "gặp 2 người" thì nói chuyện với một người hai lần cũng
// xong — đúng cái bẫy mà kiểu mục tiêu này mang theo.
//
// ⚠️ Mã NPC `senior_sister` là KHOÁ NGHIỆP VỤ có dòng DB thật trỏ vào (`npc_template.code`, seed
// V37) — ADR-038 D5 cấm đổi. Tên hiển thị ("Tô Vân Nhi") nằm ở bundle i18n, không nằm ở đây.
//
// =============================================================================================
// 200 EXP — VÀ VÌ SAO NÓ LÀ CON SỐ NHỎ NHẤT TRONG GAME
// =============================================================================================
// Đây là quest ĐẦU TIÊN không tốn gì cả: Tô Vân Nhi đứng ở x=-9, nằm TRỌN trong vùng an toàn
// `sect_grounds` ([-15, 5], V35), và trên đường đi từ điểm vào world (x=0) không có một điểm spawn
// nào. Không mất máu, không có rủi ro, không cần trang bị. Ba quest có đánh nhau của arc 1 đều
// tính phần thưởng theo QUÃNG ĐƯỜNG + số nhát chịu đòn (V30 §2, V33 §2); quest này không có cả hai
// thứ đó để mà tính.
//
// Nên ràng buộc ở đây là ràng buộc NGƯỢC: phần thưởng phải nhỏ hơn hẳn quest rẻ nhất có đánh nhau
// (`kill_training_dummies`, 500 EXP + 3 × 20), nếu không nó thành quest có lãi/rủi ro tốt nhất
// game và dạy người chơi đúng một điều sai. 200 là khoảng 1/3 quest 1 — thấy được trên thanh EXP,
// không đủ để đáng đi vòng lại.
//
// ✅ KHÔNG phá bất biến "đường tối thiểu của arc 1 dừng ĐÚNG Cấp 10". Quest này đứng NGOÀI chuỗi
//    đó (tiên quyết của nó là `awakening_trial` đã CLAIMED, tức chuỗi arc 1 đã đóng xong):
//    4.585 + 200 = 4.785, Cấp 11 cần 5.500 ⇒ vẫn Cấp 10, cửa sổ chọn hệ phái vẫn là thứ người chơi
//    gặp ngay sau khảo hạch chứ không bị quest này đẩy qua. `AwakeningTrialProgressionTest` không
//    đụng tới quest này và không cần đụng.
//
// ⚠️ CÒN TREO — HỎI CHỦ DỰ ÁN (không phải việc AI tự quyết, CLAUDE.md §1): STORY.md §6 xếp arc 1 ở
//    Lv1–18 và arc 2 ở Lv19–36, nhưng trọn chuỗi arc 1 chỉ đưa người chơi tới ĐÚNG Cấp 10. Quãng
//    Lv10–18 hiện KHÔNG có nội dung nào. Quest này cố ý KHÔNG giả vờ lấp quãng đó bằng một cục EXP
//    to — nó chỉ mở cánh cửa tường thuật sang arc 2. Lấp bằng cái gì (nhiệm vụ ngày? thêm quest arc
//    1? kéo giãn đường cong EXP?) là quyết định thiết kế của chủ dự án.
//
// 2 viên Tiểu Hồi Nguyên Đan: món quà sư tỷ cho đàn em (§5 — "ấm áp, che chở đàn em"), và cũng là
// lần đầu người chơi có đan dược mà KHÔNG phải bỏ 80 vàng mua (V36). ADR-046 vừa dựng xong nguồn
// hồi máu; đây là chỗ đầu tiên game tự đưa nó vào tay người chơi.
// =============================================================================================
({
    on_accept(ctx) {
        ctx.save_progress(JSON.stringify({ reported: false }));
    },

    on_event(ctx, event) {
        var progress = JSON.parse(ctx.progress_json);
        if (event.type === "npc_talked" && event.npc_code === "senior_sister") {
            progress.reported = true;
        }
        ctx.save_progress(JSON.stringify(progress));
        if (progress.reported) {
            ctx.complete();
        }
    },

    on_claim(ctx) {
        ctx.give_exp(200);
        ctx.give_item("minor_healing_pill", 2);
    }
})
