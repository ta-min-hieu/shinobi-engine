package dev.kage.dialogue.api;

import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;
import java.util.List;
import java.util.Optional;

/**
 * API công khai của {@code feature-dialogue} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ tự chạy {@code ScriptSandbox}/đọc {@code npc_template} trực tiếp (Điều 24).
 */
public final class DialogueFacade {

    private final GetDialogueNodeUseCase getDialogueNodeUseCase;

    public DialogueFacade(GetDialogueNodeUseCase getDialogueNodeUseCase) {
        this.getDialogueNodeUseCase = getDialogueNodeUseCase;
    }

    /** Bắt đầu hội thoại — luôn node {@link DialogueNode#START_NODE_ID}. Rỗng nếu NPC không có dialogue. */
    public Optional<DialogueNode> startDialogue(String npcCode) {
        return getDialogueNodeUseCase.execute(npcCode, DialogueNode.START_NODE_ID);
    }

    /**
     * Chọn 1 lựa chọn trong node hiện tại — {@code optionIndex} là VỊ TRÍ trong node
     * {@code currentNodeId}, KHÔNG phải chuỗi {@code action} (server tự tra lại action thật từ
     * script theo index, không tin client tự khai — Điều 24).
     */
    public SelectOptionResult selectOption(String npcCode, String currentNodeId, int optionIndex) {
        Optional<DialogueNode> currentNode = getDialogueNodeUseCase.execute(npcCode, currentNodeId);
        if (currentNode.isEmpty()) {
            return new SelectOptionResult.NotFound();
        }
        List<DialogueOption> options = currentNode.get().options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return new SelectOptionResult.NotFound();
        }
        return resolveAction(npcCode, options.get(optionIndex));
    }

    private SelectOptionResult resolveAction(String npcCode, DialogueOption option) {
        if (DialogueOption.ACTION_OPEN_SHOP.equals(option.action())) {
            return new SelectOptionResult.OpenShop();
        }
        if (DialogueOption.ACTION_END.equals(option.action())) {
            return new SelectOptionResult.Ended();
        }
        if (option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX)) {
            String questCode = option.action().substring(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX.length());
            return new SelectOptionResult.AcceptQuest(questCode);
        }
        return getDialogueNodeUseCase.execute(npcCode, option.action())
                .<SelectOptionResult>map(SelectOptionResult.Node::new)
                .orElseGet(SelectOptionResult.NotFound::new);
    }

    /**
     * Kết quả chọn lựa chọn — {@code Node} khi dẫn tới node khác, {@code AcceptQuest} khi action
     * là {@code accept_quest:<code>} (feature-dialogue KHÔNG tự gọi feature-quest — chỉ trả
     * {@code questCode}, GATEWAY mới là nơi gọi {@code QuestFacade.acceptQuest}, giữ 2 feature
     * tách biệt, cùng cách "open_shop" được gateway xử lý thay vì DialogueFacade tự gọi ShopFacade).
     */
    public sealed interface SelectOptionResult {
        record Node(DialogueNode node) implements SelectOptionResult {
        }

        record OpenShop() implements SelectOptionResult {
        }

        record Ended() implements SelectOptionResult {
        }

        record NotFound() implements SelectOptionResult {
        }

        record AcceptQuest(String questCode) implements SelectOptionResult {
        }
    }
}
