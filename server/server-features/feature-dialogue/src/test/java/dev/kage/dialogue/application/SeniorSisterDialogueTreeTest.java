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
     * ⚠️ Thứ tự bốn lựa chọn của {@code start} bị khoá bởi cùng lý do đã khoá cây Chu Hoành:
     * {@code option_index} gửi đi trên dây là VỊ TRÍ, không phải action (dialogue.proto). Chèn một
     * lựa chọn vào giữa là đổi ý nghĩa của mọi index phía sau nó.
     */
    @Test
    @DisplayName("Node start giữ ĐÚNG 4 lựa chọn theo đúng thứ tự — option_index là VỊ TRÍ")
    void startNodeKeepsItsFourOptionsInOrder() {
        DialogueNode start = node(DialogueNode.START_NODE_ID);

        assertThat(start.textKey()).isEqualTo("dialogue.senior_sister.greeting");
        assertThat(start.options())
                .extracting(DialogueOption::action)
                .containsExactly("about_rank", "about_rival", "about_herself", "end");
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
     * ⚠️ Cây này KHÔNG có nhánh nhận quest, và đó là chủ đích chứ không phải thiếu sót: quest
     * {@code report_to_outer_sect} nhận từ Chu Hoành và hoàn thành bằng chính việc MỞ LỜI với cô
     * (mục tiêu {@code npc_talked}). Thêm một lựa chọn {@code accept_quest:...} vào đây là biến mục
     * tiêu "tìm được người" thành "bấm đúng nút" — xem javadoc {@code QuestFacade.onNpcTalked}.
     */
    @Test
    @DisplayName("Không nhánh nào của cô nhận quest — mốc quest đặt ở lúc MỞ LỜI, không ở một lựa chọn")
    void noBranchAcceptsAQuest() {
        for (String nodeId : new String[] {DialogueNode.START_NODE_ID, "about_rank", "about_rival", "about_herself"}) {
            assertThat(node(nodeId).options())
                    .as("node \"%s\" mọc ra một nhánh nhận quest", nodeId)
                    .noneMatch(option -> option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX));
        }
    }

    private DialogueNode node(String nodeId) {
        Optional<DialogueNode> result = useCase.execute(NPC_CODE, nodeId);
        assertThat(result)
                .as("không đọc được node \"%s\" từ %s", nodeId, PRODUCTION_SCRIPT_REF)
                .isPresent();
        return result.get();
    }
}
