package dev.kage.dialogue.application;

import java.util.Map;

import dev.kage.dialogue.domain.DialogueFacts;

/** {@link DialogueFacts} cố định cho test — quest/vật phẩm không có trong bảng là chưa nhận / 0. */
record FixedDialogueFacts(Map<String, String> questStatuses, Map<String, Integer> itemCounts) implements DialogueFacts {

    static FixedDialogueFacts questInProgress(String questCode, String itemCode, int count) {
        return new FixedDialogueFacts(Map.of(questCode, "IN_PROGRESS"), Map.of(itemCode, count));
    }

    @Override
    public String questStatus(String questCode) {
        return questStatuses.getOrDefault(questCode, NOT_ACCEPTED);
    }

    @Override
    public int itemCount(String itemTemplateCode) {
        return itemCounts.getOrDefault(itemTemplateCode, 0);
    }
}
