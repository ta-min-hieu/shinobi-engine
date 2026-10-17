package dev.kage.dialogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.NpcTemplatePort;

class GetDialogueNodeUseCaseTest {

    /** Trỏ tới script test THẬT trong classpath (không phải resource town_elder.js sản phẩm). */
    private static final String TEST_SCRIPT_REF = "/scripts/dialogue/test_fixture.js";

    static final class FakeNpcTemplatePort implements NpcTemplatePort {
        private final String scriptRef;

        FakeNpcTemplatePort(String scriptRef) {
            this.scriptRef = scriptRef;
        }

        @Override
        public Optional<String> findDialogueScriptRefByCode(String npcCode) {
            // Giống adapter thật: Postgres từ chối U+0000 trong tham số chuỗi, kể cả ở câu SELECT.
            if (npcCode.indexOf('\0') >= 0) {
                throw new IllegalStateException("Postgres từ chối U+0000 trong tham số chuỗi");
            }
            return "known_npc".equals(npcCode) ? Optional.of(scriptRef) : Optional.empty();
        }
    }

    @Test
    void shouldReturnEmpty_whenNpcCodeUnknown() {
        GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        Optional<DialogueNode> result = useCase.execute("unknown_npc", DialogueNode.START_NODE_ID);

        assertThat(result).isEmpty();
    }

    /** Bug 2026-10-02: mã NPC chứa U+0000 từng tới câu SELECT — Postgres từ chối, client nhận 500. */
    @Test
    void shouldReturnEmpty_whenNpcCodeContainsNul() {
        GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        Optional<DialogueNode> result = useCase.execute("known\0npc", DialogueNode.START_NODE_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnStartNode_withOptions() {
        GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        Optional<DialogueNode> result = useCase.execute("known_npc", DialogueNode.START_NODE_ID);

        assertThat(result).isPresent();
        DialogueNode node = result.get();
        assertThat(node.nodeId()).isEqualTo("start");
        assertThat(node.textKey()).isEqualTo("dialogue.test.greeting");
        assertThat(node.options()).hasSize(2);
        assertThat(node.options().get(0).labelKey()).isEqualTo("dialogue.option.ask");
        assertThat(node.options().get(0).action()).isEqualTo("more");
        assertThat(node.options().get(1).action()).isEqualTo("end");
    }

    @Test
    void shouldReturnSecondNode_whenNavigatingByNodeId() {
        GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        Optional<DialogueNode> result = useCase.execute("known_npc", "more");

        assertThat(result).isPresent();
        assertThat(result.get().textKey()).isEqualTo("dialogue.test.more");
        assertThat(result.get().options()).hasSize(1);
    }

    @Test
    void shouldReturnEmpty_whenNodeIdDoesNotExistInScript() {
        GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        Optional<DialogueNode> result = useCase.execute("known_npc", "no_such_node");

        assertThat(result).isEmpty();
    }

    /**
     * {@code nodeId} đi thẳng từ packet ({@code SelectDialogueOptionCommand.current_node_id}) — client
     * gửi gì cũng được. Cây hội thoại là một object JS, nên tên của thuộc tính KẾ THỪA từ
     * {@code Object.prototype} ({@code constructor}, {@code toString}...) cũng "có mặt" trên nó:
     * trước bản vá, chúng lọt qua phép tra node rồi làm extractor ném lỗi, gateway trả 500 kèm một
     * stack trace ở mức ERROR thay vì 404 "không có lựa chọn này".
     */
    @ParameterizedTest
    @ValueSource(strings = {"constructor", "toString", "valueOf", "hasOwnProperty", "__proto__", "__defineGetter__"})
    void shouldReturnEmpty_whenNodeIdNamesAnInheritedObjectProperty(String nodeId) {
        GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        Optional<DialogueNode> result = useCase.execute("known_npc", nodeId);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenScriptRefDoesNotExistOnClasspath() {
        GetDialogueNodeUseCase useCase =
                new GetDialogueNodeUseCase(new FakeNpcTemplatePort("/scripts/dialogue/does_not_exist.js"));

        // loadScript ném IllegalStateException (không tìm thấy resource) — hành vi hiện tại là
        // để lỗi lập trình này lộ ra ngay (fail fast), không nuốt thành Optional.empty() im lặng.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute("known_npc", "start"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Dòng 77(e) (2026-10-17): node thiếu/sai kiểu trường bắt buộc là lỗi SCRIPT — trả rỗng (gateway
     * báo 404 như node không tồn tại) và ghi log, không ném NPE/ClassCastException ra thành lỗi 500.
     */
    @ParameterizedTest
    @ValueSource(strings = {"missing_text", "text_not_string", "options_missing", "option_missing_action"})
    void shouldReturnEmpty_whenANodeIsMalformed(String nodeId) {
        GetDialogueNodeUseCase useCase =
                new GetDialogueNodeUseCase(new FakeNpcTemplatePort("/scripts/dialogue/test_fixture_malformed.js"));

        assertThat(useCase.execute("known_npc", nodeId)).isEmpty();
    }
}
