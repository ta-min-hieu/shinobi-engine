// town_elder.js — nội dung hội thoại NPC "town_elder" (Điều 27, GAMEPLAY.md §9.2), KHÔNG hardcode
// Java. Trả về TOÀN BỘ cây node (object literal, key = nodeId) — Java (GetDialogueNodeUseCase)
// trích đúng 1 node cần dùng mỗi lần gọi, không giữ state giữa các lần (script tự chứa dữ liệu).
//
// STORY: arc-1 §6 (Tạp Dịch) — bắt buộc khai theo STORY.md §8 quy tắc 1.
// Nhân vật: trưởng lão Chu Hoành (STORY.md §5), tông môn Thanh Hư (STORY.md §4.1).
//
// ⚠️ NHỊP HÉ LỘ (STORY.md §8 quy tắc 2): arc 1 chỉ được GIEO MẦM. Chu Hoành ĐƯỢC nói về tông môn,
// về thang cảnh giới, về chuyện chính ông kẹt ở Kim Đan. Ông KHÔNG được nhắc tới Thiên Môn, tới
// chân tướng Thiên Địa Đại Kiếp, hay tới mảnh ngọc cổ — những thứ đó thuộc arc 3 và arc 5. Thêm
// nhánh mới vào đây thì kiểm lại ranh giới đó trước.
//
// ⚠️ TÊN KHOÁ LÀ DI SẢN: node `about_town` và khoá `dialogue.town_elder.about_town` ra đời thời bối
// cảnh làng ninja. ADR-038 D7.2 cấm đổi khoá (khoá đi thẳng qua mạng trong text_key, và
// DialogueIntegrationTest kiểm đúng chuỗi này), nên khoá còn chữ "town" trong khi nội dung đã là
// tông môn tu tiên. Đọc theo nội dung, đừng đọc theo tên khoá.
//
// text_key/label_key là khoá i18n (client tự tra bản dịch), KHÔNG phải text hiển thị thẳng.
// action của option là 1 trong: tên node khác trong cây này (điều hướng), "open_shop" (mở cửa
// hàng toàn cục — GAMEPLAY.md §7.2), "end" (kết thúc hội thoại), "accept_quest:<code>" (nhận quest
// — GAMEPLAY.md §9.3, gateway gọi QuestFacade.acceptQuest, feature-dialogue không biết feature-quest).
//
// ⚠️ Bốn lựa chọn của node `start` GIỮ NGUYÊN cả thứ tự lẫn action: `option_index` là VỊ TRÍ trong
// node (dialogue.proto), nên chèn thêm một lựa chọn vào giữa là đổi ý nghĩa của mọi index phía sau.
// Nội dung mới của arc 1 vì vậy mọc XUỐNG DƯỚI qua `about_town`, không chen ngang ở `start`.
({
    start: {
        text_key: "dialogue.town_elder.greeting",
        options: [
            { label_key: "dialogue.option.ask_about_town", action: "about_town" },
            { label_key: "dialogue.option.accept_quest_kill_dummies", action: "accept_quest:kill_training_dummies" },
            { label_key: "dialogue.option.open_shop", action: "open_shop" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // "Tông môn là gì" — giới thiệu Thanh Hư Tông và thân phận tạp dịch của người chơi.
    about_town: {
        text_key: "dialogue.town_elder.about_town",
        options: [
            { label_key: "dialogue.option.ask_about_realm", action: "about_realm" },
            { label_key: "dialogue.option.ask_about_elder", action: "about_elder" },
            { label_key: "dialogue.option.ask_night_watch", action: "night_watch" },
            { label_key: "dialogue.option.ask_old_archive", action: "old_archive" },
            { label_key: "dialogue.option.ask_awakening_trial", action: "awakening_trial" },
            { label_key: "dialogue.option.ask_outer_sect", action: "outer_sect" },
            { label_key: "dialogue.option.ask_missing_disciple", action: "missing_disciple" },
            { label_key: "dialogue.option.ask_confront_steward", action: "confront_steward" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // CAO TRÀO arc 1 (STORY.md §6): phiên trực đêm — quest thứ hai của game. Mọc xuống dưới qua
    // `about_town` chứ KHÔNG chen vào `start`: 4 lựa chọn của `start` bị khoá bởi option_index
    // (xem đầu file + TownElderDialogueTreeTest).
    //
    // Nhịp hé lộ (§8 quy tắc 2): Chu Hoành chỉ nói tới chuyện trước mắt — linh thảo bị trộm, ai đó
    // lảng vảng ngoài rào. Ông KHÔNG được nhắc Huyết Sa Giáo (arc 2 mới lộ), càng không nhắc
    // Thiên Môn hay mảnh ngọc cổ.
    night_watch: {
        text_key: "dialogue.town_elder.night_watch",
        options: [
            { label_key: "dialogue.option.accept_quest_night_watch", action: "accept_quest:night_watch_bandit" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // GIEO MẦM arc 1 (STORY.md §6): dọn góc Tàng Thư Đường bỏ hoang — quest thứ BA của game. Cũng
    // mọc xuống dưới qua `about_town`, cùng lý do với `night_watch` (option_index của `start` bị
    // khoá). Nó là lựa chọn ÁP CHÓT của `about_town`, đặt sau `night_watch` để đọc đúng thứ tự kể.
    //
    // Nhịp hé lộ (§8 quy tắc 2): Chu Hoành chỉ nói về VIỆC DỌN DẸP và về mấy cỗ máy cũ còn cựa
    // quậy. Ông KHÔNG biết — và tuyệt đối không được nhắc — mảnh ngọc cổ mà người chơi sẽ nhặt
    // được ở đó (phần thưởng quest, clear_old_archive.js); nó chỉ là "vật may mắn" ở arc 1, tới
    // arc 3 mới phát sáng. Ông cũng không nhắc Linh Triều hay Thiên Môn: với ông đó là "đồ cổ của
    // người xưa", không hơn.
    old_archive: {
        text_key: "dialogue.town_elder.old_archive",
        options: [
            { label_key: "dialogue.option.accept_quest_old_archive", action: "accept_quest:clear_old_archive" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // NHỊP ĐÓNG arc 1 (STORY.md §6): kỳ khảo hạch thức tỉnh linh căn — quest thứ TƯ của game, và
    // là quest đầu tiên có một mốc CƠ CHẾ nằm ngay sau nó (Cấp 10 ⇒ mở khoá chọn hệ phái,
    // `SelectFactionUseCase.MIN_LEVEL`). Cũng mọc xuống dưới qua `about_town`, cùng lý do với
    // `night_watch`/`old_archive` (option_index của `start` bị khoá).
    //
    // Nhịp hé lộ (§8 quy tắc 2): Chu Hoành nói về KỲ THI và về việc chính ông đứng ra bảo lãnh.
    // Ông KHÔNG được nhắc mảnh ngọc cổ người chơi vừa nhặt ở quest 3 (ông không biết nó tồn tại —
    // xem `old_archive`), không nhắc Thiên Môn, không nhắc Huyết Sa. Linh căn thức tỉnh vì người
    // chơi trụ được, không vì di vật nào — đó chính là luận đề ở nhánh `about_elder`.
    //
    // ⚠️ Lời thoại NÓI THẲNG rằng quãng đường tới sân khảo hạch là một phần bài thi. Đó không phải
    // câu văn cho đẹp: sân ở x=-47, đi từ tông môn tới đó phải lách qua hai cỗ thạch vệ và tốn 40
    // máu ĐO ĐƯỢC (V33 §3). V30 từng để người chơi tự phát hiện chuyện đó giữa đường và trả giá
    // bằng một buổi playtest — lần này bản đồ và lời thoại nói cùng một câu ngay từ đầu.
    awakening_trial: {
        text_key: "dialogue.town_elder.awakening_trial",
        options: [
            { label_key: "dialogue.option.accept_quest_awakening_trial", action: "accept_quest:awakening_trial" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // ⚠️ NODE ĐẦU TIÊN CỦA ARC 2 (STORY.md §6 — Ngoại Môn) trong cây này, và cũng là chỗ Chu Hoành
    // BÀN GIAO người chơi cho người khác. Tới đây arc 1 đã đóng: khảo hạch xong, linh căn đã thức
    // tỉnh, thân phận tạp dịch hết hiệu lực. Ông không còn là người giao việc chính nữa.
    //
    // Nhịp hé lộ (§8 quy tắc 2): arc 2 mở bằng chuyện THỨ HẠNG và TÀI NGUYÊN — nguyên văn §6 arc 2:
    // "tài nguyên tu luyện ngoại môn phân phát theo thứ hạng — người chơi phải cày từ đáy bảng".
    // Chu Hoành ĐƯỢC nói về chuyện đó và về Tô Vân Nhi (§5). Ông KHÔNG được nhắc chuỗi đệ tử mất
    // tích, càng không nhắc hai chữ Huyết Sa — §6 xếp cả hai vào TWIST giữa arc 2, không phải chỗ
    // mở màn. Bạch Kính Chi thì để chính Tô Vân Nhi kể (senior_sister.js), vì cô là người đang
    // chịu trận, còn ông lão thì không.
    outer_sect: {
        text_key: "dialogue.town_elder.outer_sect",
        options: [
            { label_key: "dialogue.option.accept_quest_report_to_outer_sect", action: "accept_quest:report_to_outer_sect" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // Thang cảnh giới (ADR-038 D2) — dạy người chơi đọc con số cấp độ của chính mình, và đặt mốc
    // "Luyện Khí tầng 5 thì đo được linh căn" khớp điều kiện chọn linh căn ở cấp 10 (GDD §5).
    about_realm: {
        text_key: "dialogue.town_elder.about_realm",
        options: [
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // Động cơ của Chu Hoành (STORY.md §5) — cũng chính là luận đề của cả game: linh căn quyết định
    // đi NHANH tới đâu, không quyết định đi ĐƯỢC tới đâu.
    about_elder: {
        text_key: "dialogue.town_elder.about_elder",
        options: [
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // ĐIỀU TRA arc 2 (STORY.md §6): Tô Vân Nhi nhờ người chơi hỏi ông về chuyện đệ tử ngoại môn
    // không thấy về. Mọc xuống dưới qua `about_town`, cùng lý do với `night_watch`/`old_archive`:
    // bốn lựa chọn của `start` bị khoá bởi option_index.
    //
    // §5 Chu Hoành "biết nhiều hơn những gì ông nói": ông nói ra được hai điều — Hình Đường đã
    // KẾT LUẬN là bỏ trốn, và đây không phải đứa đầu tiên. Ông KHÔNG được nhắc hang luyện công,
    // dấu vết hút tu vi, hay Huyết Sa: cả ba là nhịp TWIST của arc 2 (§8 quy tắc 2), và ông cũng
    // thật sự chưa biết.
    missing_disciple: {
        text_key: "dialogue.town_elder.missing_disciple",
        options: [
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // ⚠️ CAO TRÀO arc 2 (STORY.md §6) — quest THỨ CHÍN, và là chỗ arc 2 đóng. Nối thẳng vào
    // `missing_disciple` bên trên: ông lão không tin cái sổ ở quest 7, và đây là lúc ông biết vì
    // sao cái sổ đóng nhanh như vậy.
    //
    // Nhịp hé lộ (§8 quy tắc 2): tới ĐÂY Chu Hoành mới được chỉ đích danh Hình Vân Sơn, vì cao
    // trào arc 2 CHÍNH LÀ "hộ pháp Huyết Sa cải trang làm chấp sự". Ông vẫn KHÔNG được nhắc:
    //   · Thiên Môn / chân tướng Đại Kiếp — arc 3 gieo nghi, arc 6 mới lộ;
    //   · chuyện trong tông có nội gián CẤP TRƯỞNG LÃO — arc 3 twist, danh tính giữ tới arc 5;
    //   · và tuyệt đối không chạm tới Tô Vân Nhi (twist arc 5).
    // Ông nói được đúng thứ ông suy ra ĐƯỢC từ vật chứng trong tay: cái lệnh phù quest 8 mang về
    // trùng dấu với người đã ký đóng hồ sơ. Đó là suy luận của một ông già cẩn thận, không phải
    // lời tiên tri.
    confront_steward: {
        text_key: "dialogue.town_elder.confront_steward",
        options: [
            { label_key: "dialogue.option.accept_quest_confront_steward",
              action: "accept_quest:confront_the_steward" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    }
})
