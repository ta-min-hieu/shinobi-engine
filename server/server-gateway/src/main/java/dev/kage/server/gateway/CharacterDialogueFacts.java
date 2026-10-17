package dev.kage.server.gateway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.kage.dialogue.domain.DialogueFacts;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.domain.QuestLogEntry;

/**
 * {@link DialogueFacts} của MỘT nhân vật cho MỘT lượt hội thoại (dòng 65(c)) — dựng trên luồng
 * blocking, mỗi lệnh {@code TalkToNpc}/{@code SelectDialogueOption} một bản mới.
 *
 * <p>Đọc LƯỜI và nhớ trong lượt: cây hội thoại không hỏi gì (đa số NPC) thì không tốn câu truy vấn
 * nào; hỏi nhiều lần thì chỉ đọc một lần. Không nhớ xuyên lượt — người chơi vừa nhặt thêm đồ thì lần
 * mở lời sau phải thấy.
 */
final class CharacterDialogueFacts implements DialogueFacts {

    private final long characterId;
    private final QuestFacade questFacade;
    private final InventoryFacade inventoryFacade;

    private Map<String, String> questStatuses;
    private Map<String, Integer> itemCounts;
    private Map<String, String> questProgress;

    CharacterDialogueFacts(long characterId, QuestFacade questFacade, InventoryFacade inventoryFacade) {
        this.characterId = characterId;
        this.questFacade = questFacade;
        this.inventoryFacade = inventoryFacade;
    }

    @Override
    public String questStatus(String questCode) {
        if (questStatuses == null) {
            questStatuses = new HashMap<>();
            for (QuestLogEntry entry : questFacade.listQuests(characterId)) {
                questStatuses.put(entry.questCode(), entry.status().name());
            }
        }
        return questStatuses.getOrDefault(questCode, NOT_ACCEPTED);
    }

    @Override
    public int itemCount(String itemTemplateCode) {
        if (itemCounts == null) {
            itemCounts = countByCode();
        }
        return itemCounts.getOrDefault(itemTemplateCode, 0);
    }

    @Override
    public String questProgressJson(String questCode) {
        if (questProgress == null) {
            questProgress = questFacade.inProgressJson(characterId);
        }
        return questProgress.getOrDefault(questCode, NO_PROGRESS);
    }

    private Map<String, Integer> countByCode() {
        Map<Long, String> codeById = new HashMap<>();
        for (ItemCatalogEntry entry : inventoryFacade.itemCatalog().entries()) {
            codeById.put(entry.id(), entry.code());
        }
        Map<String, Integer> counts = new HashMap<>();
        List<ItemStack> items =
                inventoryFacade.listInventory(characterId).inventory().items();
        for (ItemStack stack : items) {
            String code = codeById.get(stack.itemTemplateId());
            if (code != null) {
                counts.merge(code, stack.quantity(), Integer::sum);
            }
        }
        return counts;
    }
}
