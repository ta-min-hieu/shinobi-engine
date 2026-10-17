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

    /** Fixture chưa cần tiến độ thì trả như chưa làm; xem {@link WithProgress}. */
    @Override
    public String questProgressJson(String questCode) {
        return NO_PROGRESS;
    }

    /** {@link FixedDialogueFacts} kèm tiến độ quest cố định. */
    record WithProgress(FixedDialogueFacts base, Map<String, String> progressJsonByCode) implements DialogueFacts {

        @Override
        public String questStatus(String questCode) {
            return base.questStatus(questCode);
        }

        @Override
        public int itemCount(String itemTemplateCode) {
            return base.itemCount(itemTemplateCode);
        }

        @Override
        public String questProgressJson(String questCode) {
            return progressJsonByCode.getOrDefault(questCode, NO_PROGRESS);
        }
    }
}
