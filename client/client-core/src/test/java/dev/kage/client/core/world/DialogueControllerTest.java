package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.DialogueOptionView;

/**
 * {@link DialogueController} thuần Java, không GL — test được thẳng, cùng lý do
 * {@link CombatHudControllerTest} test được trạng thái HUD mà không cần cửa sổ.
 */
class DialogueControllerTest {

    private final DialogueController controller = new DialogueController();

    private static DialogueNodeResponse node(String nodeId, String textKey, String... optionLabels) {
        DialogueNodeResponse.Builder builder =
                DialogueNodeResponse.newBuilder().setNodeId(nodeId).setTextKey(textKey);
        for (String label : optionLabels) {
            builder.addOptions(DialogueOptionView.newBuilder().setLabelKey(label));
        }
        return builder.build();
    }

    @Test
    void startsClosed() {
        assertThat(controller.isOpen()).isFalse();
    }

    @Test
    void remembersNpcCodeBeforeTheFirstNodeArrives() {
        // Điểm mấu chốt của lớp này: DialogueNodeResponse KHÔNG mang npc_code, nhưng
        // SelectDialogueOptionCommand thì bắt buộc phải có. Không nhớ ngay lúc click là mất hẳn.
        assertThat(controller.beginTalk("town_elder")).isTrue();

        assertThat(controller.isOpen()).isTrue();
        assertThat(controller.npcCode()).isEqualTo("town_elder");
        assertThat(controller.isAwaitingFirstNode()).isTrue();
    }

    @Test
    void refusesASecondConversationWhileOneIsOpen() {
        controller.beginTalk("town_elder");

        assertThat(controller.beginTalk("shop_keeper")).isFalse();
        assertThat(controller.npcCode()).isEqualTo("town_elder");
    }

    @Test
    void exposesNodeContentInServerOrder() {
        controller.beginTalk("town_elder");

        controller.onNode(node("start", "dialogue.town_elder.greeting", "opt.a", "opt.b", "opt.c"));

        assertThat(controller.currentNodeId()).isEqualTo("start");
        assertThat(controller.textKey()).isEqualTo("dialogue.town_elder.greeting");
        // Thứ tự LÀ hợp đồng: chỉ số trong danh sách này chính là option_index gửi ngược lại.
        assertThat(controller.optionLabelKeys()).containsExactly("opt.a", "opt.b", "opt.c");
        assertThat(controller.isAwaitingFirstNode()).isFalse();
    }

    @Test
    void replacesOptionsWhenMovingToAnotherNode() {
        controller.beginTalk("town_elder");
        controller.onNode(node("start", "greeting", "opt.a", "opt.b", "opt.c"));

        controller.onNode(node("about_town", "about", "opt.bye"));

        assertThat(controller.currentNodeId()).isEqualTo("about_town");
        // KHÔNG cộng dồn: gửi index 2 cho node chỉ có 1 lựa chọn là chọn nhầm nhánh của cây.
        assertThat(controller.optionLabelKeys()).containsExactly("opt.bye");
        assertThat(controller.isSelectableOption(1)).isFalse();
    }

    @Test
    void onlyAcceptsIndexesThatExistInTheCurrentNode() {
        controller.beginTalk("town_elder");
        controller.onNode(node("start", "greeting", "opt.a", "opt.b"));

        assertThat(controller.isSelectableOption(0)).isTrue();
        assertThat(controller.isSelectableOption(1)).isTrue();
        assertThat(controller.isSelectableOption(2)).isFalse();
        assertThat(controller.isSelectableOption(-1)).isFalse();
    }

    @Test
    void selectsNothingWhenClosed() {
        controller.beginTalk("town_elder");
        controller.onNode(node("start", "greeting", "opt.a"));

        controller.close();

        assertThat(controller.isOpen()).isFalse();
        assertThat(controller.isSelectableOption(0)).isFalse();
        assertThat(controller.optionLabelKeys()).isEmpty();
    }

    @Test
    void canStartAgainAfterClosing() {
        controller.beginTalk("town_elder");
        controller.close();

        assertThat(controller.beginTalk("town_elder")).isTrue();
    }

    /**
     * Server từ chối lệnh hội thoại bằng ErrorResponse mang sequenceId của lệnh (đứng xa NPC quá, từ
     * 2026-10-11). Controller phải nhận ra đó là lệnh CỦA MÌNH để coordinator đóng cuộc thoại — không
     * thì lần bấm NPC sau bị chính cuộc dở dang này chặn.
     */
    @Test
    void recognisesTheRejectionOfItsOwnPendingCommand() {
        controller.beginTalk("town_elder");
        controller.onCommandSent(41);

        assertThat(controller.isPendingCommand(41)).isTrue();
        assertThat(controller.isPendingCommand(40))
                .as("lỗi của luồng khác không phải của cuộc thoại này")
                .isFalse();
    }

    @Test
    void forgetsThePendingCommandOnceItIsAnsweredOrTheTalkCloses() {
        controller.beginTalk("town_elder");
        controller.onCommandSent(41);
        controller.onNode(node("start", "dialogue.town_elder.greeting", "opt.a"));
        assertThat(controller.isPendingCommand(41)).isFalse();

        controller.onCommandSent(42);
        controller.close();
        assertThat(controller.isPendingCommand(42)).isFalse();
        assertThat(controller.beginTalk("senior_sister"))
                .as("đóng xong thì bắt chuyện lại được")
                .isTrue();
    }
}
