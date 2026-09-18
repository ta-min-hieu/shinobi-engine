// waypost_attendant.js — nội dung hội thoại NPC "waypost_attendant" (Điều 27, GAMEPLAY.md §9.2),
// KHÔNG hardcode Java. Trả về TOÀN BỘ cây node (object literal, key = nodeId), giống town_elder.js.
//
// STORY: arc-2 §6 (Ngoại Môn) — bắt buộc khai theo STORY.md §8 quy tắc 1.
//
// ⚠️ NPC THỨ BA CỦA DỰ ÁN, và là NPC đầu tiên KHÔNG có tên riêng: cùng một `npc_template` đứng ở
// SÁU chỗ (V49). Đó là chủ đích — xem ADR-050 D6 phương án (a):
//   · `dialogue_script_ref` là NOT NULL và `GatewayBootstrap.validateContentScripts` duyệt mọi ref
//     lúc khởi động, nên mỗi NPC riêng là một cây thoại phải viết VÀ phải nuôi mãi về sau;
//   · sáu người trực trạm không cần sáu tính cách — họ cần nói đúng MỘT điều: chỗ này nghỉ được.
// Vì vậy lời thoại ở đây cố ý viết ở ngôi VÔ DANH ("đệ tử trực trạm"), không xưng tên, không kể
// thân thế. Thêm tên riêng cho một người trong số họ là đổi thiết kế, không phải thêm chữ.
//
// ⚠️ NHỊP HÉ LỘ (STORY.md §8 quy tắc 2). Người trực trạm ĐƯỢC nói về:
//   · công dụng của trạm (trận pháp hộ thân cỡ nhỏ — cùng cơ chế trận pháp hộ sơn của tông, §7);
//   · đường ra rìa Hoang Vực nguy hiểm thế nào (§6 arc 2 "Zone": lần đầu thấy RÌA Hoang Vực).
// KHÔNG được nhắc tới:
//   · hai chữ Huyết Sa và chuỗi đệ tử mất tích — twist giữa arc 2, người chơi phải tự lần ra;
//   · bất cứ thứ gì của arc 3 trở đi (`ProductionDialogueTreesTest` gác bằng máy).
//
// ⚠️ Trạm KHÔNG hồi đầy máu (ADR-050 D4) và lời thoại phải nói đúng điều đó — nghỉ ở đây là ĐỨNG
// YÊN CHỜ, không phải bấm một cái là đầy. Viết sai chỗ này là hứa với người chơi một thứ cơ chế
// không làm.
//
// text_key/label_key là khoá i18n. action: tên node khác (điều hướng), hoặc "end" (kết thúc).
// Không có "accept_quest:" nào ở đây: trạm là tiện ích, không phải nguồn nhiệm vụ.
({
    start: {
        text_key: "dialogue.waypost_attendant.greeting",
        options: [
            { label_key: "dialogue.option.ask_waypost_rest", action: "about_rest" },
            { label_key: "dialogue.option.ask_waypost_road", action: "about_road" },
            { label_key: "dialogue.option.bye_waypost", action: "end" }
        ]
    },
    // Công dụng thật của trạm, nói bằng lời người chơi kiểm chứng được ngay: đứng yên thì máu lên.
    // Đây là chỗ DẠY cơ chế hồi máu ngoài chiến đấu (ADR-046 D2) — trước ADR-050 người chơi không
    // có chỗ nào ở nửa cuối trục để thấy nó chạy.
    about_rest: {
        text_key: "dialogue.waypost_attendant.about_rest",
        options: [
            { label_key: "dialogue.option.ask_waypost_road", action: "about_road" },
            { label_key: "dialogue.option.bye_waypost", action: "end" }
        ]
    },
    // Vì sao trạm nằm ở ĐÂY chứ không phải chỗ khác — và cảnh báo về thứ đợi phía trước.
    about_road: {
        text_key: "dialogue.waypost_attendant.about_road",
        options: [
            { label_key: "dialogue.option.ask_waypost_rest", action: "about_rest" },
            { label_key: "dialogue.option.bye_waypost", action: "end" }
        ]
    }
})
