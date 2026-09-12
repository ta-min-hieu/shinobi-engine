package dev.kage.dialogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;

/**
 * Kiểm NỘI DUNG cây hội thoại sản phẩm {@code senior_sister.js} — Tô Vân Nhi (STORY.md §5), NPC
 * THỨ HAI của dự án và là NPC đầu tiên của arc 2 (seed V37).
 *
 * <p>Ba bất biến CẤU TRÚC chung cho mọi cây nằm ở {@link ProductionDialogueTreesTest}; lớp này chỉ
 * gác những thứ đúng với riêng cô.
 */
class SeniorSisterDialogueTreeTest {

    /** Đường dẫn classpath THẬT của script sản phẩm — cùng chuỗi mà migration V37 ghi vào DB. */
    private static final String PRODUCTION_SCRIPT_REF = "/scripts/dialogue/senior_sister.js";

    private static final String NPC_CODE = "senior_sister";

    private final GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(
            npcCode -> NPC_CODE.equals(npcCode) ? Optional.of(PRODUCTION_SCRIPT_REF) : Optional.empty());

    /**
     * ⚠️ Thứ tự các lựa chọn của {@code start} bị khoá bởi cùng lý do đã khoá cây Chu Hoành:
     * {@code option_index} gửi đi trên dây là VỊ TRÍ, không phải action (dialogue.proto). Chèn một
     * lựa chọn vào giữa là đổi ý nghĩa của mọi index phía sau nó.
     *
     * <p>Lựa chọn thứ tư ({@code about_work}, thêm cùng quest 6 — V38) vì thế được đặt NGAY TRƯỚC
     * {@code end}: đó là vị trí làm xê dịch ít index cũ nhất (đúng một cái), và "xin phép" vẫn là
     * dòng cuối như mọi cây khác.
     */
    @Test
    @DisplayName("Node start giữ ĐÚNG 5 lựa chọn theo đúng thứ tự — option_index là VỊ TRÍ")
    void startNodeKeepsItsOptionsInOrder() {
        DialogueNode start = node(DialogueNode.START_NODE_ID);

        assertThat(start.textKey()).isEqualTo("dialogue.senior_sister.greeting");
        assertThat(start.options())
                .extracting(DialogueOption::action)
                .containsExactly("about_rank", "about_rival", "about_herself", "about_work", "end");
    }

    /**
     * Nhánh giao việc — quest 6 {@code recover_outer_sect_tallies} (V38, STORY.md §6 arc 2 nhịp
     * VIỆC VẶT). Đây là lần đầu Tô Vân Nhi GIAO một quest, nên nó phải là một lựa chọn thoại có ý
     * thức của người chơi — khác hẳn quest 5, thứ hoàn thành bằng chính việc mở lời với cô.
     */
    @Test
    @DisplayName("Nhánh giao việc nhận được quest 6 và quest 7, đúng mã và đúng thứ tự kể")
    void workBranchOffersTheTallyQuest() {
        DialogueNode work = node("about_work");

        assertThat(work.textKey()).isEqualTo("dialogue.senior_sister.about_work");
        assertThat(work.options())
                .extracting(DialogueOption::action)
                .containsExactly(
                        "accept_quest:recover_outer_sect_tallies",
                        "accept_quest:missing_disciple_trail",
                        "start",
                        "end");
    }

    /**
     * §6 arc 2 "Xung đột" — tài nguyên ngoại môn phân phát theo thứ hạng. Đây là lý do TƯỜNG THUẬT
     * của nhiệm vụ ngày + bảng xếp hạng (§7); nói ra một lần ở đây để hai hệ thống đó có chỗ dựa
     * sẵn khi lên.
     */
    @Test
    @DisplayName("Nhánh thứ hạng nói đúng chuyện tài nguyên ngoại môn")
    void rankBranchExplainsHowResourcesAreHandedOut() {
        DialogueNode rank = node("about_rank");

        assertThat(rank.textKey()).isEqualTo("dialogue.senior_sister.about_rank");
        assertThat(rank.options()).extracting(DialogueOption::action).containsExactly("start", "end");
    }

    /** §5 Bạch Kính Chi: kiêu nhưng KHÔNG hèn — nhánh này tồn tại để arc 3 có cái mà vượt. */
    @Test
    @DisplayName("Nhánh đối thủ giới thiệu Bạch Kính Chi")
    void rivalBranchIntroducesTheRival() {
        DialogueNode rival = node("about_rival");

        assertThat(rival.textKey()).isEqualTo("dialogue.senior_sister.about_rival");
        assertThat(rival.options()).extracting(DialogueOption::action).containsExactly("start", "end");
    }

    /** §5 Tô Vân Nhi: xuất thân thương gia sa sút — gốc để twist arc 5 về sau không rơi từ trên trời. */
    @Test
    @DisplayName("Nhánh thân thế kể xuất thân của Tô Vân Nhi")
    void herOwnBranchTellsHerBackground() {
        DialogueNode herself = node("about_herself");

        assertThat(herself.textKey()).isEqualTo("dialogue.senior_sister.about_herself");
        assertThat(herself.options()).extracting(DialogueOption::action).containsExactly("start", "end");
    }

    /**
     * ⚠️ ĐÚNG MỘT nhánh của cô được nhận quest, và đó là {@code about_work} (quest 6, kiểm ở
     * {@link #workBranchOffersTheTallyQuest}). Ba nhánh kể chuyện thì KHÔNG — và quan trọng hơn:
     * cả cây KHÔNG có lựa chọn nào để NỘP quest.
     *
     * <p>Hai quest của cô đặt mốc ở lúc MỞ LỜI, không ở một lựa chọn: quest 5 hoàn thành khi người
     * chơi tìm ra cô, quest 6 giao thẻ bài khi người chơi quay lại gặp cô. Thêm một lựa chọn "nộp
     * thẻ bài" là biến mục tiêu "mang về" thành "bấm đúng nút" — xem javadoc
     * {@code QuestFacade.onNpcTalked} và GAMEPLAY.md §9.5.
     */
    @Test
    @DisplayName("Chỉ nhánh giao việc nhận quest; không nhánh nào NỘP quest — mốc đặt ở lúc MỞ LỜI")
    void onlyTheWorkBranchAcceptsAQuest() {
        for (String nodeId : new String[] {DialogueNode.START_NODE_ID, "about_rank", "about_rival", "about_herself"}) {
            assertThat(node(nodeId).options())
                    .as("node \"%s\" mọc ra một nhánh nhận quest", nodeId)
                    .noneMatch(option -> option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX));
        }

        long questOptions = java.util.Arrays.stream(new String[] {
                    DialogueNode.START_NODE_ID, "about_rank", "about_rival", "about_herself", "about_work"
                })
                .flatMap(nodeId -> node(nodeId).options().stream())
                .filter(option -> option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX))
                .count();

        assertThat(questOptions)
                .as("mọi lựa chọn nhận quest phải nằm gọn trong nhánh giao việc — chỗ khác là mốc quest trôi đi")
                .isEqualTo(2L);
    }

    private DialogueNode node(String nodeId) {
        Optional<DialogueNode> result = useCase.execute(NPC_CODE, nodeId);
        assertThat(result)
                .as("không đọc được node \"%s\" từ %s", nodeId, PRODUCTION_SCRIPT_REF)
                .isPresent();
        return result.get();
    }
}
