package dev.kage.quest.application;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/** Nhận quest {@code questCode} — chạy {@code on_accept} lấy tiến độ khởi điểm, ghi status IN_PROGRESS. */
public final class AcceptQuestUseCase {

    private final QuestProgressPort progressPort;
    private final QuestTemplatePort templatePort;
    private final TransactionRunner tx;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    public AcceptQuestUseCase(QuestProgressPort progressPort, QuestTemplatePort templatePort, TransactionRunner tx) {
        this.progressPort = progressPort;
        this.templatePort = templatePort;
        this.tx = tx;
    }

    public AcceptResult execute(long characterId, String questCode) {
        QuestTemplate template = templatePort.findByCode(questCode).orElse(null);
        if (template == null) {
            return AcceptResult.QUEST_NOT_FOUND;
        }
        if (progressPort.find(characterId, template.id()).isPresent()) {
            return AcceptResult.ALREADY_ACCEPTED;
        }
        if (!prerequisiteMet(characterId, template)) {
            return AcceptResult.PREREQUISITE_NOT_MET;
        }

        QuestScriptRunner.HookEffects effects = scriptRunner.runAccept(template.scriptRef());
        tx.inTransaction((Transaction t) -> progressPort.save(
                t, new QuestProgress(characterId, template.id(), QuestStatus.IN_PROGRESS, effects.progressJson())));
        return AcceptResult.OK;
    }

    /**
     * ADR-044 — quest tiên quyết phải ở trạng thái {@link QuestStatus#CLAIMED}.
     *
     * <p>Vì sao CLAIMED chứ không phải COMPLETED (chủ dự án chốt 2026-09-05): chuỗi kể chuyện chỉ
     * đóng khi người chơi QUAY LẠI gặp NPC lĩnh thưởng, và CLAIMED là trạng thái cuối duy nhất
     * không bao giờ lùi.
     *
     * <p>⚠️ KHÔNG kiểm bắc cầu: chuỗi A→B→C chỉ kiểm B khi nhận C. Đủ, vì muốn có B thì đã phải qua
     * A — nhưng ghi ra để không ai tưởng nó soi cả chuỗi.
     */
    private boolean prerequisiteMet(long characterId, QuestTemplate template) {
        if (template.prerequisiteQuestId().isEmpty()) {
            return true;
        }
        return progressPort
                .find(characterId, template.prerequisiteQuestId().getAsLong())
                .filter(progress -> progress.status() == QuestStatus.CLAIMED)
                .isPresent();
    }

    public enum AcceptResult {
        OK,
        QUEST_NOT_FOUND,
        ALREADY_ACCEPTED,
        /** ADR-044 — quest tiên quyết chưa lĩnh thưởng xong. */
        PREREQUISITE_NOT_MET
    }
}
