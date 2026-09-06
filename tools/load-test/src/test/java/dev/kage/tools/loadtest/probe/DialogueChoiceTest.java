package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.DialogueOptionView;

/**
 * Dịch một bước dòng lệnh thành {@code option_index} — thuần logic, không cần server.
 *
 * <p>Node dựng theo đúng {@code start} của {@code town_elder.js} (4 lựa chọn, thứ tự BỊ KHOÁ bởi
 * {@code option_index}) để test nói về đúng cây hội thoại đang chạy thật.
 */
final class DialogueChoiceTest {

    private static final List<DialogueOptionView> START_NODE_OPTIONS = List.of(
            option("dialogue.option.ask_about_town", "about_town"),
            option("dialogue.option.accept_quest_kill_dummies", "accept_quest:kill_training_dummies"),
            option("dialogue.option.open_shop", "open_shop"),
            option("dialogue.option.bye", "end"));

    @Test
    void shouldTakeNumericStepAsIndex() {
        assertThat(DialogueChoice.resolve("0", START_NODE_OPTIONS)).isZero();
        assertThat(DialogueChoice.resolve("3", START_NODE_OPTIONS)).isEqualTo(3);
    }

    @Test
    void shouldMatchStepAgainstAction() {
        assertThat(DialogueChoice.resolve("accept_quest:kill_training_dummies", START_NODE_OPTIONS))
                .isEqualTo(1);
        assertThat(DialogueChoice.resolve("end", START_NODE_OPTIONS)).isEqualTo(3);
    }

    @Test
    void shouldMatchStepAgainstLabelKey_whenNoActionMatches() {
        assertThat(DialogueChoice.resolve("dialogue.option.open_shop", START_NODE_OPTIONS))
                .isEqualTo(2);
    }

    /** {@code action} thắng {@code label_key} khi cả hai khớp — action là thứ server thật sự chạy. */
    @Test
    void shouldPreferAction_whenBothFieldsCouldMatch() {
        List<DialogueOptionView> ambiguous = List.of(option("end", "about_town"), option("dialogue.bye", "end"));

        assertThat(DialogueChoice.resolve("end", ambiguous)).isEqualTo(1);
    }

    @Test
    void shouldFailLoudlyAndListOptions_whenStepMatchesNothing() {
        assertThatThrownBy(() -> DialogueChoice.resolve("accept_quest:khong_co_that", START_NODE_OPTIONS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accept_quest:khong_co_that")
                .hasMessageContaining("about_town")
                .hasMessageContaining("open_shop");
    }

    @Test
    void shouldFailLoudly_whenIndexIsOutsideTheNode() {
        assertThatThrownBy(() -> DialogueChoice.resolve("4", START_NODE_OPTIONS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4 lựa chọn");
    }

    @Test
    void shouldRenderEveryOptionWithItsIndex() {
        assertThat(DialogueChoice.describe(START_NODE_OPTIONS))
                .contains("[0] dialogue.option.ask_about_town -> about_town")
                .contains("[3] dialogue.option.bye -> end");
    }

    private static DialogueOptionView option(String labelKey, String action) {
        return DialogueOptionView.newBuilder()
                .setLabelKey(labelKey)
                .setAction(action)
                .build();
    }
}
