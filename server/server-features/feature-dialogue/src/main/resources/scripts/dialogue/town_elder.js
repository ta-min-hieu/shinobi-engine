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
//
// CÂU CHÀO THEO VIỆC ĐANG DỞ (dòng 65(c), chủ dự án duyệt 2026-10-17): mở lời khi `foundation_pill_run`
// đang làm mà chưa đủ 3 viên đan (`take_item` của script quest) thì ông nhắc, thay vì chào như thường
// rồi để quest đứng im. Đủ đan mà chưa hạ đủ 2 Yêu Nhân thì ông nói đúng chỗ thiếu đó — đọc
// `felled` từ tiến độ RIÊNG của script quest (`ctx.quest_progress`, chỉ đọc). Hình dạng
// `{felled, delivered}` là của foundation_pill_run.js: đổi bên đó thì đổi cả đây.
var greetingKey = "dialogue.town_elder.greeting";
if (ctx.quest_status("foundation_pill_run") === "IN_PROGRESS") {
    var pillRun = JSON.parse(ctx.quest_progress("foundation_pill_run"));
    if (ctx.item_count("minor_healing_pill") < 3) {
        greetingKey = "dialogue.town_elder.greeting_pills_missing";
    } else if ((pillRun.felled || 0) < 2) {
        greetingKey = "dialogue.town_elder.greeting_beasts_missing";
    }
}
({
    start: {
        text_key: greetingKey,
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
            // Quãng Lv10-18 của arc 1 (V45) — BA nhánh, và chúng đứng TRƯỚC `outer_sect` vì arc 1
            // phải đọc xong rồi mới tới nhịp bàn giao sang arc 2. Ba lựa chọn phía sau vì thế lùi
            // ba chỗ; `option_index` là VỊ TRÍ (dialogue.proto) nên đây là thay đổi CÓ CHỦ ĐÍCH,
            // ghim lại bằng TownElderDialogueTreeTest — cùng luật V39/V40 đã áp khi chèn nhánh mới.
            { label_key: "dialogue.option.ask_spirit_vein", action: "spirit_vein" },
            { label_key: "dialogue.option.ask_first_manual", action: "first_manual" },
            { label_key: "dialogue.option.ask_raider_band", action: "raider_band" },
            { label_key: "dialogue.option.ask_outer_sect", action: "outer_sect" },
            { label_key: "dialogue.option.ask_missing_disciple", action: "missing_disciple" },
            { label_key: "dialogue.option.ask_confront_steward", action: "confront_steward" },
            // Quang Lv22-27 (V47) - ba nhanh DUNG SAU confront_steward vi chung noi tiep
            // dung cai ket dang do cua no. option_index la VI TRI (dialogue.proto) nen
            // chen o day day "bye" lui ba cho: co chu dich, ghim bang TownElderDialogueTreeTest.
            { label_key: "dialogue.option.ask_dead_vein", action: "dead_vein" },
            { label_key: "dialogue.option.ask_blood_sand_trail", action: "blood_sand_trail" },
            { label_key: "dialogue.option.ask_scatter_band", action: "scatter_band" },
            { label_key: "dialogue.option.ask_wasteland_edge", action: "wasteland_edge" },
            { label_key: "dialogue.option.ask_rank_board", action: "rank_board" },
            { label_key: "dialogue.option.ask_pill_run", action: "pill_run" },
            { label_key: "dialogue.option.ask_proving", action: "proving" },
            { label_key: "dialogue.option.ask_breakthrough", action: "breakthrough" },
            // Nhiệm vụ ngày (ADR-055, STORY §7) — đứng CUỐI, sau mọi nhánh chính tuyến, ngay trước "bye":
            // `bye` lùi một chỗ là CÓ CHỦ ĐÍCH (option_index là VỊ TRÍ), ghim ở TownElderDialogueTreeTest.
            { label_key: "dialogue.option.ask_daily_duty", action: "daily_duty" },
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
    // QUÃNG Lv10-18 CỦA ARC 1 (STORY.md §6) — ba nhánh, thêm 2026-09-13 cùng V45. Trước chúng, cả
    // nửa sau của dải Lv1-18 mà §6 khai cho arc 1 chưa từng tồn tại: bốn quest cũ đưa người chơi
    // tới ĐÚNG Cấp 10 rồi dừng. Ba nhánh này khép nó ở Cấp 18.
    //
    // Nhịp hé lộ (§8 quy tắc 2): vẫn arc 1, nên đám đoạt mạch là cướp THƯỜNG. Chu Hoành KHÔNG được
    // nhắc Huyết Sa (arc 2 mới lộ, quest 8), càng không nhắc Thiên Môn hay mảnh ngọc cổ. Ông nói
    // đúng thứ một ông già coi việc ngoại môn biết: có người chiếm mạch, đệ tử phải đi vòng.
    spirit_vein: {
        text_key: "dialogue.town_elder.spirit_vein",
        options: [
            { label_key: "dialogue.option.accept_quest_spirit_vein", action: "accept_quest:spirit_vein_raiders" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // "Công pháp đầu tiên (Lv15)" của §6 — mốc DUY NHẤT của arc 1 chưa từng được làm. Nó là một BỘ
    // TRANG BỊ `required_level 15` (V45), không phải cơ chế học chiêu mới: trong code không có cơ
    // chế mở khoá chiêu theo cấp nào, và dựng một cái là việc cần ADR riêng (CLAUDE.md §4).
    //
    // ⚠️ Lời thoại NÓI THẲNG rằng bộ pháp y là thứ cần cho việc TIẾP THEO, không phải thưởng cho
    // việc đi đòi sách. Đó không phải câu văn cho đẹp: `RaiderBandBalanceTest` đo được mặc bộ Tạp
    // Dịch đi đánh đầu lĩnh là THUA kể cả trên máu đầy. Bản đồ, lời thoại và phép tính nói cùng một
    // câu — đúng bài học V33 đã trả giá khi V30 để người chơi tự phát hiện giữa đường.
    first_manual: {
        text_key: "dialogue.town_elder.first_manual",
        options: [
            { label_key: "dialogue.option.accept_quest_first_manual", action: "accept_quest:first_cultivation_manual" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // NHỊP ĐÓNG của arc 1. Hạ đầu lĩnh xong là Cấp 18, và câu cuối của ông lão ("ta có chuyện khác
    // muốn bàn") chính là chỗ nối sang nhánh `outer_sect` ngay dưới — arc 2 chỉ mở sau nhánh này,
    // và V45 thi hành điều đó bằng `prerequisite_quest_id` chứ không bằng lời hứa.
    raider_band: {
        text_key: "dialogue.town_elder.raider_band",
        options: [
            { label_key: "dialogue.option.accept_quest_raider_band", action: "accept_quest:break_the_raider_band" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    // Quang Lv22-27 cua arc 2 (V47). Ba nhanh nay noi tiep cai ket dang do cua `confront_steward`:
    // ho phap chet, nhung thu han dao do o vung mach chet phia dong thi van nam day.
    //
    // NHIP HE LO (STORY §8 quy tac 2): o nhanh DAU ong lao CHUA biet chung la Huyet Sa - chi biet
    // "nguoi la". Cai ten roi xuong o nhanh THU HAI, khi nguoi choi tu nhin thay dau hut tu vi.
    dead_vein: {
        text_key: "dialogue.town_elder.dead_vein",
        options: [
            { label_key: "dialogue.option.accept_quest_dead_vein", action: "accept_quest:dead_vein_intruders" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },

    // --- V48, quãng Lv28–36: nhịp ĐÓNG arc 2 ---------------------------------------------------
    // ⚠️ Năm nhánh này lẽ ra phải đi CÙNG V48. Migration và script quest đã lên master mà không có
    // một lối vào nào — nội dung tồn tại trong DB nhưng người chơi không chạm tới được. Bài học:
    // thêm quest = thêm hàng `quest_template` + script + NHÁNH THOẠI, thiếu cái thứ ba thì hai cái
    // kia là đồ trang trí.
    wasteland_edge: {
        text_key: "dialogue.town_elder.wasteland_edge",
        options: [
            { label_key: "dialogue.option.accept_quest_wasteland_edge", action: "accept_quest:wasteland_edge_patrol" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },

    rank_board: {
        text_key: "dialogue.town_elder.rank_board",
        options: [
            { label_key: "dialogue.option.accept_quest_rank_board", action: "accept_quest:rank_board_dispute" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },

    pill_run: {
        text_key: "dialogue.town_elder.pill_run",
        options: [
            { label_key: "dialogue.option.accept_quest_pill_run", action: "accept_quest:foundation_pill_run" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },

    proving: {
        text_key: "dialogue.town_elder.proving",
        options: [
            { label_key: "dialogue.option.accept_quest_proving", action: "accept_quest:outer_sect_proving" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },

    breakthrough: {
        text_key: "dialogue.town_elder.breakthrough",
        options: [
            { label_key: "dialogue.option.accept_quest_breakthrough", action: "accept_quest:foundation_breakthrough" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    blood_sand_trail: {
        text_key: "dialogue.town_elder.blood_sand_trail",
        options: [
            { label_key: "dialogue.option.accept_quest_blood_sand_trail", action: "accept_quest:blood_sand_trail" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    },
    scatter_band: {
        text_key: "dialogue.town_elder.scatter_band",
        options: [
            { label_key: "dialogue.option.accept_quest_scatter_band", action: "accept_quest:scatter_the_blood_sand_band" },
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
    },
    // NHIỆM VỤ NGÀY (ADR-055) — STORY §7 "công việc tông môn", §8 quy tắc 5: có lý do tường thuật,
    // không cốt truyện riêng, được phép hài nhẹ. Không nhắc gì vượt nhịp arc 1.
    daily_duty: {
        text_key: "dialogue.town_elder.daily_duty",
        options: [
            { label_key: "dialogue.option.accept_quest_daily_dummy_drill", action: "accept_quest:daily_dummy_drill" },
            { label_key: "dialogue.option.accept_quest_daily_mountain_patrol", action: "accept_quest:daily_mountain_patrol" },
            { label_key: "dialogue.option.accept_quest_daily_water_duty", action: "accept_quest:daily_water_duty" },
            { label_key: "dialogue.option.back", action: "about_town" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    }
})
