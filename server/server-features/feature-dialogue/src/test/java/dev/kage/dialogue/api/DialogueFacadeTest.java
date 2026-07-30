package dev.kage.dialogue.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.NpcTemplatePort;

class DialogueFacadeTest {

    private static final String SCRIPT_REF = "/scripts/dialogue/test_fixture.js";

    private static DialogueFacade newFacade() {
        NpcTemplatePort npcTemplatePort =
                npcCode -> "known_npc".equals(npcCode) ? Optional.of(SCRIPT_REF) : Optional.empty();
        return new DialogueFacade(new GetDialogueNodeUseCase(npcTemplatePort));
    }

    @Test
    void shouldReturnEmpty_whenStartingDialogueWithUnknownNpc() {
        DialogueFacade facade = newFacade();

        assertThat(facade.startDialogue("unknown_npc")).isEmpty();
    }

    @Test
    void shouldReturnStartNode_whenNpcKnown() {
        DialogueFacade facade = newFacade();

        Optional<DialogueNode> node = facade.startDialogue("known_npc");

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("start");
    }

    @Test
    void shouldNavigateToNextNode_whenOptionActionIsNodeId() {
        DialogueFacade facade = newFacade();

        // option index 0 ở "start" (test_fixture.js) có action="more".
        DialogueFacade.SelectOptionResult result = facade.selectOption("known_npc", "start", 0);

        assertThat(result).isInstanceOf(DialogueFacade.SelectOptionResult.Node.class);
        DialogueFacade.SelectOptionResult.Node node = (DialogueFacade.SelectOptionResult.Node) result;
        assertThat(node.node().nodeId()).isEqualTo("more");
    }

    @Test
    void shouldReturnEnded_whenOptionActionIsEnd() {
        DialogueFacade facade = newFacade();

        // option index 1 ở "start" có action="end".
        DialogueFacade.SelectOptionResult result = facade.selectOption("known_npc", "start", 1);

        assertThat(result).isInstanceOf(DialogueFacade.SelectOptionResult.Ended.class);
    }

    @Test
    void shouldReturnNotFound_whenOptionIndexOutOfBounds() {
        DialogueFacade facade = newFacade();

        DialogueFacade.SelectOptionResult result = facade.selectOption("known_npc", "start", 99);

        assertThat(result).isInstanceOf(DialogueFacade.SelectOptionResult.NotFound.class);
    }

    @Test
    void shouldReturnNotFound_whenCurrentNodeDoesNotExist() {
        DialogueFacade facade = newFacade();

        DialogueFacade.SelectOptionResult result = facade.selectOption("known_npc", "no_such_node", 0);

        assertThat(result).isInstanceOf(DialogueFacade.SelectOptionResult.NotFound.class);
    }

    @Test
    void shouldReturnOpenShop_whenScriptOptionActionIsOpenShop() {
        // Script riêng cho test này: "start" có option action="open_shop".
        NpcTemplatePort npcTemplatePort = npcCode ->
                "shop_npc".equals(npcCode) ? Optional.of("/scripts/dialogue/test_fixture_shop.js") : Optional.empty();
        DialogueFacade facade = new DialogueFacade(new GetDialogueNodeUseCase(npcTemplatePort));

        DialogueFacade.SelectOptionResult result = facade.selectOption("shop_npc", "start", 0);

        assertThat(result).isInstanceOf(DialogueFacade.SelectOptionResult.OpenShop.class);
    }
}
