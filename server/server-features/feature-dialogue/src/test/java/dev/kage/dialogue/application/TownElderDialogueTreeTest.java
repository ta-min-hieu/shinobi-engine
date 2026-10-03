package dev.kage.dialogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;

/**
 * Kiểm chính CÂY HỘI THOẠI SẢN PHẨM {@code town_elder.js} — khác
 * {@link GetDialogueNodeUseCaseTest} vốn cố ý chỉ dùng fixture test.
 *
 * <p><b>Vì sao cần lớp này.</b> Trước đợt re-theme tu tiên (ADR-038),
 * script sản phẩm chỉ được kiểm ở MỘT chỗ duy nhất: {@code DialogueIntegrationTest} bên
 * {@code server-database}, và test đó cần Postgres qua Testcontainers. Máy không bật Docker thì nó
 * bị BỎ QUA trong im lặng còn build vẫn xanh — nghĩa là nội dung hội thoại có thể hỏng mà không ai
 * biết. Cây này không cần DB để đọc: {@code NpcTemplatePort} chỉ trả về một đường dẫn classpath.
 *
 * <p><b>Thứ được gác ở ĐÂY: nội dung.</b> Đúng lời thoại nào, đúng mã quest nào, đúng thứ tự lựa
 * chọn nào — những thứ chỉ đúng với riêng Chu Hoành.
 *
 * <p><b>Thứ KHÔNG còn ở đây (2026-09-09).</b> Ba bất biến CẤU TRÚC (không node cụt, không action
 * trỏ vào hư không, không lộ nội dung arc sau) đã chuyển sang {@link ProductionDialogueTreesTest}
 * và nay chạy cho MỌI NPC sản phẩm. Chúng nằm ở đây khi thế giới chỉ có một NPC; NPC thứ hai
 * ({@code senior_sister}, V37) làm lộ ra rằng chúng chưa bao giờ là chuyện riêng của ông lão.
 */
class TownElderDialogueTreeTest {

    /** Đường dẫn classpath THẬT của script sản phẩm — cùng chuỗi mà migration V10 ghi vào DB. */
    private static final String PRODUCTION_SCRIPT_REF = "/scripts/dialogue/town_elder.js";

    private static final String NPC_CODE = "town_elder";

    private final GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(
            npcCode -> NPC_CODE.equals(npcCode) ? Optional.of(PRODUCTION_SCRIPT_REF) : Optional.empty());

    @Test
    @DisplayName("Node start giữ ĐÚNG 4 lựa chọn theo đúng thứ tự — option_index là VỊ TRÍ")
    void startNodeKeepsItsFourOptionsInOrder() {
        DialogueNode start = node(DialogueNode.START_NODE_ID);

        // Chèn/xoá/đảo một lựa chọn ở đây là đổi ý nghĩa của mọi option_index phía sau nó
        // (dialogue.proto gửi VỊ TRÍ chứ không gửi action), nên thứ tự được ghim bằng test.
        assertThat(start.options())
                .extracting(DialogueOption::action)
                .containsExactly("about_town", "accept_quest:kill_training_dummies", "open_shop", "end");
    }

    @Test
    @DisplayName("Arc 1 mọc xuống dưới: hỏi về tông môn dẫn tiếp được sang cảnh giới và thân thế trưởng lão")
    void outerSectBranchLeadsToTheArcOneContent() {
        DialogueNode aboutSect = node("about_town");

        assertThat(aboutSect.textKey()).isEqualTo("dialogue.town_elder.about_town");
        assertThat(aboutSect.options())
                .extracting(DialogueOption::action)
                .containsExactly(
                        "about_realm",
                        "about_elder",
                        "night_watch",
                        "old_archive",
                        "awakening_trial",
                        // V45 — quãng Lv10-18 của arc 1, chèn TRƯỚC `outer_sect` vì arc 1 phải đọc
                        // xong rồi mới tới nhịp bàn giao sang arc 2. Ba lựa chọn sau đó lùi ba chỗ;
                        // `option_index` là VỊ TRÍ nên đây là thay đổi CÓ CHỦ ĐÍCH, và ô test này
                        // chính là chỗ ghim nó lại.
                        "spirit_vein",
                        "first_manual",
                        "raider_band",
                        "outer_sect",
                        "missing_disciple",
                        "confront_steward",
                        // V47 — quãng Lv22–27 của arc 2, chèn SAU `confront_steward` vì cả ba nối
                        // tiếp đúng cái kết dang dở của nó. Chỉ `end` lùi ba chỗ; `option_index` là
                        // VỊ TRÍ nên đây là thay đổi CÓ CHỦ ĐÍCH, ghim lại ngay tại đây.
                        "dead_vein",
                        "blood_sand_trail",
                        "scatter_band",
                        // V48 — quãng Lv28–36, chèn SAU `scatter_band` vì đây là đoạn khép arc 2 ở
                        // Trúc Cơ tầng 9. Chỉ `end` lùi năm chỗ; `option_index` là VỊ TRÍ nên đây là
                        // thay đổi CÓ CHỦ ĐÍCH, ghim lại ngay tại đây.
                        "wasteland_edge",
                        "rank_board",
                        "pill_run",
                        "proving",
                        "breakthrough",
                        "end");
        assertThat(node("about_realm").textKey()).isEqualTo("dialogue.town_elder.about_realm");
        assertThat(node("about_elder").textKey()).isEqualTo("dialogue.town_elder.about_elder");
    }

    /**
     * Cao trào arc 1 (STORY.md §6) — quest THỨ HAI của game, và là lý do
     * {@code ProcessQuestEventUseCase} phải bỏ hardcode một-mã-quest.
     *
     * <p>Nhánh này mọc XUỐNG DƯỚI qua {@code about_town} chứ không chen vào {@code start}: bốn lựa
     * chọn của {@code start} bị khoá bởi {@code option_index} (test ngay trên).
     */
    @Test
    @DisplayName("Phiên trực đêm nhận được quest thứ hai, và nhận đúng mã night_watch_bandit")
    void nightWatchBranchOffersTheSecondQuest() {
        DialogueNode nightWatch = node("night_watch");

        assertThat(nightWatch.textKey()).isEqualTo("dialogue.town_elder.night_watch");
        assertThat(nightWatch.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:night_watch_bandit", "about_town", "end");
    }

    /**
     * Nhịp "gieo mầm" của arc 1 (STORY.md §6) — quest THỨ BA của game, và là quest đầu tiên được
     * thêm vào mà KHÔNG phải sửa một dòng Java sản phẩm nào (V30 + một script + một nhánh ở đây).
     *
     * <p>Nhánh cũng mọc xuống dưới qua {@code about_town}, cùng lý do với {@code night_watch}.
     */
    @Test
    @DisplayName("Nhánh thư khố bỏ hoang nhận được quest thứ ba, và nhận đúng mã clear_old_archive")
    void oldArchiveBranchOffersTheThirdQuest() {
        DialogueNode oldArchive = node("old_archive");

        assertThat(oldArchive.textKey()).isEqualTo("dialogue.town_elder.old_archive");
        assertThat(oldArchive.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:clear_old_archive", "about_town", "end");
    }

    /**
     * Nhịp ĐÓNG của arc 1 (STORY.md §6) — quest THỨ TƯ, và là quest đầu tiên có một mốc CƠ CHẾ nằm
     * ngay sau nó: lĩnh thưởng xong là Cấp 10, tức là {@code SelectFactionUseCase.MIN_LEVEL} mở
     * khoá chọn hệ phái. Phép tính giữ lời hứa đó nằm ở
     * {@code QuestIntegrationTest.shouldLandExactlyAtLevelTen_afterTheWholeArcOneChain}.
     *
     * <p>Nhánh cũng mọc xuống dưới qua {@code about_town}, cùng lý do với ba nhánh quest kia.
     */
    @Test
    @DisplayName("Nhánh khảo hạch nhận được quest thứ tư, và nhận đúng mã awakening_trial")
    void awakeningTrialBranchOffersTheFourthQuest() {
        DialogueNode trial = node("awakening_trial");

        assertThat(trial.textKey()).isEqualTo("dialogue.town_elder.awakening_trial");
        assertThat(trial.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:awakening_trial", "about_town", "end");
    }

    /**
     * Nhịp MỞ của arc 2 (STORY.md §6 — Ngoại Môn), thêm 2026-09-09: Chu Hoành BÀN GIAO người chơi
     * cho Tô Vân Nhi. Đây là node đầu tiên của cây này KHÔNG thuộc arc 1.
     *
     * <p>Cũng mọc xuống dưới qua {@code about_town} — bốn lựa chọn của {@code start} vẫn bị khoá bởi
     * {@code option_index} (test đầu lớp này), và luật đó không đổi vì đổi arc.
     *
     * <p>Ba bất biến cấu trúc của cây (không node cụt, không action trỏ vào hư không, không lộ arc
     * sau) nay nằm ở {@link ProductionDialogueTreesTest} và chạy cho CẢ HAI NPC.
     */
    @Test
    @DisplayName("Nhánh ngoại môn nhận được quest thứ năm, và nhận đúng mã report_to_outer_sect")
    void outerSectBranchOffersTheFifthQuest() {
        DialogueNode outerSect = node("outer_sect");

        assertThat(outerSect.textKey()).isEqualTo("dialogue.town_elder.outer_sect");
        assertThat(outerSect.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:report_to_outer_sect", "about_town", "end");
    }

    /**
     * CAO TRÀO arc 2 (STORY.md §6) — quest THỨ CHÍN, chỗ arc 2 đóng.
     *
     * <p>Nhánh nối thẳng vào {@code missing_disciple}: ở quest 7 ông lão nói ông không tin cái sổ,
     * ở đây ông biết vì sao cái sổ đóng nhanh như vậy. Cũng mọc xuống dưới qua {@code about_town},
     * cùng luật {@code option_index} với năm nhánh quest trước.
     */
    @Test
    @DisplayName("Nhánh đối chất nhận được quest thứ chín, và nhận đúng mã confront_the_steward")
    void confrontStewardBranchOffersTheNinthQuest() {
        DialogueNode confront = node("confront_steward");

        assertThat(confront.textKey()).isEqualTo("dialogue.town_elder.confront_steward");
        assertThat(confront.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:confront_the_steward", "about_town", "end");
    }

    /**
     * Quãng Lv10–18 của arc 1 (V45) — ba nhánh lấp nửa sau của dải mà STORY.md §6 khai cho arc 1
     * nhưng chuỗi cũ chưa bao giờ chạm tới (bốn quest đưa người chơi tới ĐÚNG Cấp 10 rồi dừng).
     *
     * <p>Cả ba mọc xuống dưới qua {@code about_town} như mọi nhánh quest trước, và chúng đứng
     * TRƯỚC {@code outer_sect}: arc 1 phải đọc xong rồi mới tới nhịp bàn giao sang arc 2.
     */
    @Test
    @DisplayName("Nhánh linh mạch nhận được quest thứ mười, và nhận đúng mã spirit_vein_raiders")
    void spiritVeinBranchOffersTheTenthQuest() {
        DialogueNode spiritVein = node("spirit_vein");

        assertThat(spiritVein.textKey()).isEqualTo("dialogue.town_elder.spirit_vein");
        assertThat(spiritVein.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:spirit_vein_raiders", "about_town", "end");
    }

    /**
     * Mốc "công pháp đầu tiên (Lv15)" của STORY.md §6 — mốc DUY NHẤT của arc 1 chưa từng được làm.
     *
     * <p>Nó là một BỘ TRANG BỊ {@code required_level 15} (V45), không phải cơ chế học chiêu mới:
     * trong code không có cơ chế mở khoá chiêu theo cấp nào, và dựng một cái là việc cần ADR riêng
     * (CLAUDE.md §4). Chủ dự án chốt hướng này 2026-09-13.
     */
    @Test
    @DisplayName("Nhánh công pháp nhận được quest thứ mười một, và nhận đúng mã first_cultivation_manual")
    void firstManualBranchOffersTheEleventhQuest() {
        DialogueNode firstManual = node("first_manual");

        assertThat(firstManual.textKey()).isEqualTo("dialogue.town_elder.first_manual");
        assertThat(firstManual.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:first_cultivation_manual", "about_town", "end");
    }

    /**
     * ⚠️ Ô này tồn tại vì V48 đã lên master THIẾU đúng thứ nó gác: năm quest có hàng
     * {@code quest_template} và có script, nhưng KHÔNG có nhánh thoại nào — tức nội dung nằm trong
     * DB mà người chơi không có đường chạm tới. Thêm quest = ba thứ, không phải hai.
     */
    @Test
    @DisplayName("Năm nhánh Lv28–36 nhận đúng năm mã quest, và nhánh cuối là nhịp đóng arc 2")
    void foundationBranchesOfferTheArcTwoClosingQuests() {
        assertThat(node("wasteland_edge").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:wasteland_edge_patrol", "about_town", "end");

        assertThat(node("rank_board").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:rank_board_dispute", "about_town", "end");

        assertThat(node("pill_run").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:foundation_pill_run", "about_town", "end");

        assertThat(node("proving").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:outer_sect_proving", "about_town", "end");

        assertThat(node("breakthrough").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:foundation_breakthrough", "about_town", "end");
    }

    /**
     * Ba nhánh của quãng Lv22–27 (V47) — nối tiếp cái kết dang dở của {@code confront_steward}.
     *
     * <p>Chúng đứng SAU nó trong {@code about_town}, không chen vào giữa: hộ pháp phải chết rồi thì
     * mới có chuyện "thứ hắn đào dở vẫn nằm đấy".
     *
     * <p>⚠️ Nhịp hé lộ (STORY §8 quy tắc 2): nhánh ĐẦU ông lão chưa biết chúng là Huyết Sa — chỉ
     * biết "người lạ". Cái tên rơi xuống ở nhánh THỨ HAI, khi người chơi tự nhìn thấy dấu hút tu vi.
     */
    @Test
    @DisplayName("Ba nhánh mạch chết nhận đúng ba mã quest của quãng Lv22–27")
    void deadVeinBranchesOfferTheArcTwoUpperQuests() {
        assertThat(node("dead_vein").textKey()).isEqualTo("dialogue.town_elder.dead_vein");
        assertThat(node("dead_vein").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:dead_vein_intruders", "about_town", "end");

        assertThat(node("blood_sand_trail").textKey()).isEqualTo("dialogue.town_elder.blood_sand_trail");
        assertThat(node("blood_sand_trail").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:blood_sand_trail", "about_town", "end");

        assertThat(node("scatter_band").textKey()).isEqualTo("dialogue.town_elder.scatter_band");
        assertThat(node("scatter_band").options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:scatter_the_blood_sand_band", "about_town", "end");
    }

    /**
     * Nhịp ĐÓNG của arc 1 — và là quest mà {@code report_to_outer_sect} (arc 2 nhịp MỞ) nay lấy làm
     * tiên quyết, thay cho {@code awakening_trial} (V45 §7).
     */
    @Test
    @DisplayName("Nhánh dẹp đám đoạt mạch nhận được quest thứ mười hai, và nhận đúng mã break_the_raider_band")
    void raiderBandBranchOffersTheTwelfthQuest() {
        DialogueNode raiderBand = node("raider_band");

        assertThat(raiderBand.textKey()).isEqualTo("dialogue.town_elder.raider_band");
        assertThat(raiderBand.options())
                .extracting(DialogueOption::action)
                .containsExactly("accept_quest:break_the_raider_band", "about_town", "end");
    }

    private DialogueNode node(String nodeId) {
        Optional<DialogueNode> result = useCase.execute(NPC_CODE, nodeId);
        assertThat(result)
                .as("không đọc được node \"%s\" từ %s", nodeId, PRODUCTION_SCRIPT_REF)
                .isPresent();
        return result.get();
    }
}
