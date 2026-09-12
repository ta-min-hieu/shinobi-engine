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
                        "outer_sect",
                        "missing_disciple",
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

    private DialogueNode node(String nodeId) {
        Optional<DialogueNode> result = useCase.execute(NPC_CODE, nodeId);
        assertThat(result)
                .as("không đọc được node \"%s\" từ %s", nodeId, PRODUCTION_SCRIPT_REF)
                .isPresent();
        return result.get();
    }
}
