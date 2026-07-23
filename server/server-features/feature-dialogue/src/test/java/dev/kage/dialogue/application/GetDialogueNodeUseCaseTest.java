package dev.kage.dialogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.NpcTemplatePort;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
            return "known_npc".equals(npcCode) ? Optional.of(scriptRef) : Optional.empty();
        }
    }

    @Test
    void shouldReturnEmpty_whenNpcCodeUnknown() {
        var useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        var result = useCase.execute("unknown_npc", DialogueNode.START_NODE_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnStartNode_withOptions() {
        var useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        var result = useCase.execute("known_npc", DialogueNode.START_NODE_ID);

        assertThat(result).isPresent();
        var node = result.get();
        assertThat(node.nodeId()).isEqualTo("start");
        assertThat(node.textKey()).isEqualTo("dialogue.test.greeting");
        assertThat(node.options()).hasSize(2);
        assertThat(node.options().get(0).labelKey()).isEqualTo("dialogue.option.ask");
        assertThat(node.options().get(0).action()).isEqualTo("more");
        assertThat(node.options().get(1).action()).isEqualTo("end");
    }

    @Test
    void shouldReturnSecondNode_whenNavigatingByNodeId() {
        var useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        var result = useCase.execute("known_npc", "more");

        assertThat(result).isPresent();
        assertThat(result.get().textKey()).isEqualTo("dialogue.test.more");
        assertThat(result.get().options()).hasSize(1);
    }

    @Test
    void shouldReturnEmpty_whenNodeIdDoesNotExistInScript() {
        var useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort(TEST_SCRIPT_REF));

        var result = useCase.execute("known_npc", "no_such_node");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenScriptRefDoesNotExistOnClasspath() {
        var useCase = new GetDialogueNodeUseCase(new FakeNpcTemplatePort("/scripts/dialogue/does_not_exist.js"));

        // loadScript ném IllegalStateException (không tìm thấy resource) — hành vi hiện tại là
        // để lỗi lập trình này lộ ra ngay (fail fast), không nuốt thành Optional.empty() im lặng.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute("known_npc", "start"))
                .isInstanceOf(IllegalStateException.class);
    }
}
