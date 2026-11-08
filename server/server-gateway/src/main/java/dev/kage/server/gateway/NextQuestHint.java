package dev.kage.server.gateway;

import java.util.List;
import java.util.Map;

import dev.kage.protocol.GetNextQuestHintResponse;
import dev.kage.protocol.NextQuestHintKind;
import dev.kage.quest.domain.QuestCatalogPort;
import dev.kage.quest.domain.QuestStatus;

/**
 * Chọn gợi ý nhiệm vụ tiếp (ADR-228) — thuần, không chạm DB. Thứ tự ưu tiên: nhiệm vụ đang làm (kèm NPC cần gặp,
 * ADR-221) → nhiệm vụ đã xong chưa nhận thưởng → nhiệm vụ cốt truyện đầu tiên chưa nhận mà nhiệm vụ tiên quyết đã trả
 * xong và có NPC giao (bỏ nhiệm vụ hằng ngày) → không có gì. Điều kiện cấp / cốt truyện riêng nằm trong script hội
 * thoại — gợi ý có thể là nhiệm vụ NPC chưa chịu giao vì chưa đủ cấp.
 */
final class NextQuestHint {

    private NextQuestHint() {}

    static GetNextQuestHintResponse of(
            Map<String, QuestStatus> log, List<QuestCatalogPort.Entry> catalog, Map<String, String> givers) {
        for (Map.Entry<String, QuestStatus> entry : log.entrySet()) {
            if (entry.getValue() == QuestStatus.IN_PROGRESS) {
                return hint(
                        NextQuestHintKind.NEXT_QUEST_HINT_KIND_CONTINUE,
                        entry.getKey(),
                        QuestNpcTargets.of(entry.getKey()));
            }
        }
        for (Map.Entry<String, QuestStatus> entry : log.entrySet()) {
            if (entry.getValue() == QuestStatus.COMPLETED) {
                return hint(NextQuestHintKind.NEXT_QUEST_HINT_KIND_CLAIM, entry.getKey(), "");
            }
        }
        for (QuestCatalogPort.Entry quest : catalog) {
            if (startable(quest, log, givers)) {
                return hint(NextQuestHintKind.NEXT_QUEST_HINT_KIND_NEW, quest.code(), givers.get(quest.code()));
            }
        }
        return hint(NextQuestHintKind.NEXT_QUEST_HINT_KIND_NONE, "", "");
    }

    /** Chưa nhận, không phải hằng ngày, có NPC giao, và nhiệm vụ tiên quyết (nếu có) đã trả xong. */
    private static boolean startable(
            QuestCatalogPort.Entry quest, Map<String, QuestStatus> log, Map<String, String> givers) {
        boolean prerequisiteDone = quest.prerequisiteCode()
                .map(code -> log.get(code) == QuestStatus.CLAIMED)
                .orElse(true);
        return !quest.daily() && !log.containsKey(quest.code()) && prerequisiteDone && givers.containsKey(quest.code());
    }

    private static GetNextQuestHintResponse hint(NextQuestHintKind kind, String questCode, String npcCode) {
        return GetNextQuestHintResponse.newBuilder()
                .setKind(kind)
                .setQuestCode(questCode)
                .setNpcCode(npcCode)
                .build();
    }
}
