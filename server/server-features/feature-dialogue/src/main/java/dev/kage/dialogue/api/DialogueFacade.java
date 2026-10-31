package dev.kage.dialogue.api;

import java.util.List;
import java.util.Optional;

import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.dialogue.domain.DialogueFacts;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;

/**
 * API công khai của {@code feature-dialogue} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ tự chạy {@code ScriptSandbox}/đọc {@code npc_template} trực tiếp (Điều 24).
 */
public final class DialogueFacade {

    private final GetDialogueNodeUseCase getDialogueNodeUseCase;

    /** Dựng ở composition root. */
    public DialogueFacade(GetDialogueNodeUseCase getDialogueNodeUseCase) {
        this.getDialogueNodeUseCase = getDialogueNodeUseCase;
    }

    /** Bắt đầu hội thoại — luôn node {@link DialogueNode#START_NODE_ID}. Rỗng nếu NPC không có dialogue. */
    public Optional<DialogueNode> startDialogue(String npcCode) {
        return startDialogue(npcCode, DialogueFacts.NONE);
    }

    /**
     * Như {@link #startDialogue(String)}, nhưng script đọc được {@code facts} của người đang nói
     * chuyện (dòng 65(c)) — gateway dựng nó từ nhân vật trong SESSION.
     */
    public Optional<DialogueNode> startDialogue(String npcCode, DialogueFacts facts) {
        return getDialogueNodeUseCase.execute(npcCode, DialogueNode.START_NODE_ID, facts);
    }

    /**
     * Chọn 1 lựa chọn trong node hiện tại — {@code optionIndex} là VỊ TRÍ trong node
     * {@code currentNodeId}, KHÔNG phải chuỗi {@code action} (server tự tra lại action thật từ
     * script theo index, không tin client tự khai — Điều 24).
     */
    public SelectOptionResult selectOption(String npcCode, String currentNodeId, int optionIndex) {
        return selectOption(npcCode, currentNodeId, optionIndex, DialogueFacts.NONE);
    }

    /** Như {@link #selectOption(String, String, int)}, với {@code facts} của người đang nói chuyện. */
    public SelectOptionResult selectOption(String npcCode, String currentNodeId, int optionIndex, DialogueFacts facts) {
        Optional<DialogueNode> currentNode = getDialogueNodeUseCase.execute(npcCode, currentNodeId, facts);
        if (currentNode.isEmpty()) {
            return new SelectOptionResult.NotFound();
        }
        List<DialogueOption> options = currentNode.get().options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return new SelectOptionResult.NotFound();
        }
        return resolveAction(npcCode, options.get(optionIndex), facts);
    }

    private SelectOptionResult resolveAction(String npcCode, DialogueOption option, DialogueFacts facts) {
        if (DialogueOption.ACTION_OPEN_SHOP.equals(option.action())) {
            return new SelectOptionResult.OpenShop();
        }
        if (DialogueOption.ACTION_OPEN_STORAGE.equals(option.action())) {
            return new SelectOptionResult.OpenStorage();
        }
        if (DialogueOption.ACTION_OPEN_CRAFTING.equals(option.action())) {
            return new SelectOptionResult.OpenCrafting();
        }
        if (DialogueOption.ACTION_OPEN_FORGE.equals(option.action())) {
            return new SelectOptionResult.OpenForge();
        }
        if (DialogueOption.ACTION_OPEN_MERCHANT.equals(option.action())) {
            return new SelectOptionResult.OpenMerchant();
        }
        if (DialogueOption.ACTION_END.equals(option.action())) {
            return new SelectOptionResult.Ended();
        }
        if (option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX)) {
            String questCode = option.action().substring(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX.length());
            return new SelectOptionResult.AcceptQuest(questCode);
        }
        return getDialogueNodeUseCase
                .execute(npcCode, option.action(), facts)
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
        /** Lựa chọn dẫn tới node hội thoại khác — client vẽ {@code node}. */
        record Node(DialogueNode node) implements SelectOptionResult {}

        /** Lựa chọn mở cửa hàng — gateway gọi {@code ShopFacade}, không phải feature này. */
        record OpenShop() implements SelectOptionResult {}

        /** Lựa chọn mở kho đồ (ADR-061) — gateway gọi {@code InventoryFacade}, không phải feature này. */
        record OpenStorage() implements SelectOptionResult {}

        /** Lựa chọn mở lò đan (ADR-079) — gateway gọi {@code CraftingFacade}, không phải feature này. */
        record OpenCrafting() implements SelectOptionResult {}

        /** Lựa chọn mở lò luyện khí của thợ rèn (ADR-131) — gateway gọi {@code CraftingFacade}. */
        record OpenForge() implements SelectOptionResult {}

        /** Lựa chọn mở hàng của thương nhân du hành (ADR-131) — gateway gọi {@code TravelingMerchantFacade}. */
        record OpenMerchant() implements SelectOptionResult {}

        /** Lựa chọn kết thúc hội thoại. */
        record Ended() implements SelectOptionResult {}

        /** Node hiện tại hoặc chỉ số lựa chọn không tồn tại — client gửi dữ liệu cũ/sai. */
        record NotFound() implements SelectOptionResult {}

        /** Lựa chọn nhận quest {@code questCode} — gateway gọi {@code QuestFacade.acceptQuest}. */
        record AcceptQuest(String questCode) implements SelectOptionResult {}
    }
}
