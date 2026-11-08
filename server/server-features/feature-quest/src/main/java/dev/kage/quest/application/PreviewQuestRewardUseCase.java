package dev.kage.quest.application;

import java.util.List;
import java.util.Optional;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;

/**
 * Xem trước thưởng nhiệm vụ (ADR-242): chạy {@code on_claim} như lúc lĩnh thật nhưng BỎ HẾT hiệu ứng — script chỉ
 * ghi lại {@code give_exp/give_gold/give_item} vào một danh sách (đúng cơ chế {@code ClaimQuestRewardUseCase} dùng
 * trước khi mở giao dịch), nên không có gì chạm DB. Dùng tiến độ hiện tại của người chơi (chưa nhận ⇒ {@code "{}"}).
 */
public final class PreviewQuestRewardUseCase {

    private static final String EMPTY_PROGRESS = "{}";

    /** Thưởng dự kiến; {@code available = false} khi script không chạy được với tiến độ này. */
    public record Preview(boolean available, long exp, long gold, List<Item> items) {

        /** Một món thưởng. */
        public record Item(String itemTemplateCode, int quantity) {}
    }

    private final QuestTemplatePort templates;
    private final QuestProgressPort progress;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    /** Dựng ở composition root. */
    public PreviewQuestRewardUseCase(QuestTemplatePort templates, QuestProgressPort progress) {
        this.templates = templates;
        this.progress = progress;
    }

    /** Rỗng nếu không có nhiệm vụ mã đó. */
    public Optional<Preview> preview(long characterId, String questCode) {
        if (questCode.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        Optional<QuestTemplate> template = templates.findByCode(questCode);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        String json = progress.find(characterId, template.get().id())
                .map(QuestProgress::progressJson)
                .orElse(EMPTY_PROGRESS);
        QuestScriptRunner.HookEffects effects =
                scriptRunner.runClaim(template.get().scriptRef(), json);
        if (effects.scriptFailed()) {
            return Optional.of(new Preview(false, 0L, 0L, List.of()));
        }
        List<Preview.Item> items = effects.itemGrants().stream()
                .map(grant -> new Preview.Item(grant.itemTemplateCode(), grant.quantity()))
                .toList();
        return Optional.of(new Preview(true, effects.expGranted(), effects.goldGranted(), items));
    }
}
