// senior_sister.js — nội dung hội thoại NPC "senior_sister" (Điều 27, GAMEPLAY.md §9.2), KHÔNG
// hardcode Java. Trả về TOÀN BỘ cây node (object literal, key = nodeId), giống town_elder.js.
//
// STORY: arc-2 §6 (Ngoại Môn) — bắt buộc khai theo STORY.md §8 quy tắc 1.
// Nhân vật: Tô Vân Nhi (STORY.md §5), sư tỷ đồng hành, đệ tử ngoại môn vào trước người chơi 3 năm.
//
// ⚠️ NPC THỨ HAI CỦA DỰ ÁN. Suốt từ Phase 6 tới nay thế giới chỉ có đúng một NPC (`town_elder`),
// nên mọi thứ suy ra danh tính NPC từ một hằng số đều phải được soi lại cùng đợt này —
// `AssetCategoryResolver` (tools/asset-pipeline) là chỗ đã tự ghi sẵn lời nhắc đó.
//
// ⚠️ NHỊP HÉ LỘ (STORY.md §8 quy tắc 2). Tô Vân Nhi ĐƯỢC nói về:
//   · cách ngoại môn phân phát tài nguyên theo thứ hạng (§6 arc 2, "Xung đột");
//   · Bạch Kính Chi và chuyện hắn chèn ép "đứa tạp dịch đi cửa sau" (§6 arc 2, "Xung đột" — và §5:
//     hắn kiêu nhưng KHÔNG hèn, nên lời cô kể không được biến hắn thành côn đồ).
// Cô KHÔNG được nhắc tới:
//   · chuỗi đệ tử mất tích và hang luyện công bỏ hoang — đó là TWIST giữa arc 2, chưa tới;
//   · hai chữ Huyết Sa — §6 xếp nó vào đúng cái twist đó;
//   · và tuyệt đối không một câu nào úp mở về việc chính cô là tai mắt bị ép buộc (twist arc 5).
//     Ở arc 2 cô che chở đàn em một cách THẬT LÒNG; viết cô thành kẻ đang diễn là hỏng cả cú twist.
//
// text_key/label_key là khoá i18n. action: tên node khác (điều hướng) hoặc "end" (kết thúc).
// Cây này CHƯA có nhánh nhận quest — quest `report_to_outer_sect` nhận từ Chu Hoành và hoàn thành
// bằng chính việc MỞ LỜI với cô (mục tiêu `npc_talked`, ProcessQuestEventUseCase), không phải bằng
// một lựa chọn thoại. Xem javadoc `QuestFacade.onNpcTalked` về lý do đặt mốc ở lúc mở lời.
({
    start: {
        text_key: "dialogue.senior_sister.greeting",
        options: [
            { label_key: "dialogue.option.ask_outer_sect_rank", action: "about_rank" },
            { label_key: "dialogue.option.ask_rival", action: "about_rival" },
            { label_key: "dialogue.option.ask_senior_sister", action: "about_herself" },
            { label_key: "dialogue.option.bye_senior_sister", action: "end" }
        ]
    },
    // §6 arc 2 "Xung đột": tài nguyên ngoại môn phân phát theo thứ hạng, người chơi cày từ đáy bảng.
    // Đây là lý do TƯỜNG THUẬT cho nhiệm vụ ngày + bảng xếp hạng (§7) — nói ra ở đây một lần, để
    // khi hai hệ thống đó lên thì chúng đã có chỗ dựa sẵn trong đầu người chơi.
    about_rank: {
        text_key: "dialogue.senior_sister.about_rank",
        options: [
            { label_key: "dialogue.option.back_senior_sister", action: "start" },
            { label_key: "dialogue.option.bye_senior_sister", action: "end" }
        ]
    },
    // §5 Bạch Kính Chi: "kiêu nhưng KHÔNG hèn — gương chiếu của fantasy phàm nhân". Lời cô kể phải
    // giữ đúng chỗ đó: hắn khinh người chơi ra mặt, nhưng hắn khinh CÔNG KHAI và hắn thật sự giỏi.
    // Viết hắn thành kẻ chơi xấu sau lưng là phá mất cú "bị vượt ở arc 3" về sau.
    about_rival: {
        text_key: "dialogue.senior_sister.about_rival",
        options: [
            { label_key: "dialogue.option.back_senior_sister", action: "start" },
            { label_key: "dialogue.option.bye_senior_sister", action: "end" }
        ]
    },
    // §5 Tô Vân Nhi: xuất thân thương gia sa sút, vào trước 3 năm, ấm áp, che chở đàn em. Câu chuyện
    // gia tộc được nói ở mức người ngoài ai cũng biết — đủ để về sau twist arc 5 có gốc, không đủ
    // để người chơi đoán ra trước.
    about_herself: {
        text_key: "dialogue.senior_sister.about_herself",
        options: [
            { label_key: "dialogue.option.back_senior_sister", action: "start" },
            { label_key: "dialogue.option.bye_senior_sister", action: "end" }
        ]
    }
})
