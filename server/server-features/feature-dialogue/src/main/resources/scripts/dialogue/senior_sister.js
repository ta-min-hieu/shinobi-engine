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
// text_key/label_key là khoá i18n. action: tên node khác (điều hướng), "end" (kết thúc), hoặc
// "accept_quest:<code>" (nhận quest — GAMEPLAY.md §9.3).
//
// Quest `report_to_outer_sect` KHÔNG nhận ở đây: nó nhận từ Chu Hoành và hoàn thành bằng chính việc
// MỞ LỜI với cô (mục tiêu `npc_talked`), không phải bằng một lựa chọn thoại — xem javadoc
// `QuestFacade.onNpcTalked`. Nhánh `about_work` bên dưới thì NGƯỢC LẠI: đó là quest 6, cô là người
// GIAO, nên nó phải là một lựa chọn thoại có ý thức của người chơi.
//
// ⚠️ Và cùng một cây thoại này còn là chỗ NỘP quest 6 (mục tiêu "mang vật X tới NPC Y"): người chơi
// mở lời với cô lần nữa là đủ, KHÔNG phải bấm đúng một lựa chọn. Đừng thêm option "nộp thẻ bài" —
// mốc nằm ở lúc mở lời, và một nút nộp riêng sẽ biến mục tiêu "mang về" thành "bấm đúng nút".
//
// CÂU CHÀO THEO VIỆC ĐANG DỞ (dòng 65(c), chủ dự án duyệt 2026-10-17). Mở lời mà chưa mang đủ đồ thì
// quest không nhích và trước đây KHÔNG một lời nào nói ra — nay cô nhắc. `ctx` chỉ ĐỌC (DialogueFacts).
// Số lượng ở đây phải khớp `take_item` của hai script quest; lệch thì cô nhắc sai — test gác ở
// SeniorSisterDialogueTreeTest. Chỉ đổi text_key, KHÔNG đổi options (option_index là VỊ TRÍ).
var greetingKey = "dialogue.senior_sister.greeting";
if (ctx.quest_status("recover_outer_sect_tallies") === "IN_PROGRESS" && ctx.item_count("outer_sect_tally") < 2) {
    greetingKey = "dialogue.senior_sister.greeting_tallies_missing";
} else if (ctx.quest_status("abandoned_cave") === "IN_PROGRESS" && ctx.item_count("blood_sand_talisman") < 1) {
    greetingKey = "dialogue.senior_sister.greeting_talisman_missing";
}
({
    start: {
        text_key: greetingKey,
        options: [
            { label_key: "dialogue.option.ask_outer_sect_rank", action: "about_rank" },
            { label_key: "dialogue.option.ask_rival", action: "about_rival" },
            { label_key: "dialogue.option.ask_senior_sister", action: "about_herself" },
            { label_key: "dialogue.option.ask_outer_sect_work", action: "about_work" },
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
    },
    // §6 arc 2 "Xung đột" (việc vặt ngoại môn) + GIEO MẦM cho twist "đệ tử mất tích": một thẻ bài
    // mang tên đứa mấy hôm không thấy về, và lời giải thích CHÍNH THỨC là nó bỏ trốn — đúng nguyên
    // văn §6 ("không phải bỏ trốn như Hình Đường kết luận"). Cô tin lời đó thật lòng ở arc 2.
    // KHÔNG được nhắc: hang luyện công, hút tu vi, Huyết Sa (nhịp sau của arc, §8 quy tắc 2).
    about_work: {
        text_key: "dialogue.senior_sister.about_work",
        options: [
            { label_key: "dialogue.option.accept_quest_outer_sect_tallies",
              action: "accept_quest:recover_outer_sect_tallies" },
            // Quest 7 (V39) — nhịp ĐIỀU TRA. Đặt NGAY SAU quest 6 để hai việc của cô đọc theo đúng
            // thứ tự kể; hai lựa chọn còn lại của node này vì thế lùi một chỗ (option_index là VỊ
            // TRÍ — thay đổi CÓ CHỦ ĐÍCH, ghim lại bằng SeniorSisterDialogueTreeTest).
            { label_key: "dialogue.option.accept_quest_missing_disciple",
              action: "accept_quest:missing_disciple_trail" },
            // Quest 8 (V40) — nhịp TWIST. Cũng đặt nối đuôi, cùng lý do: hai việc trước đọc theo
            // đúng thứ tự kể, và chỉ `start`/`end` lùi chỗ (option_index là VỊ TRÍ).
            { label_key: "dialogue.option.accept_quest_abandoned_cave",
              action: "accept_quest:abandoned_cave" },
            { label_key: "dialogue.option.back_senior_sister", action: "start" },
            { label_key: "dialogue.option.bye_senior_sister", action: "end" }
        ]
    }
})
