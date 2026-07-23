package dev.kage.quest.application;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
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
        var template = templatePort.findByCode(questCode).orElse(null);
        if (template == null) {
            return AcceptResult.QUEST_NOT_FOUND;
        }
        if (progressPort.find(characterId, template.id()).isPresent()) {
            return AcceptResult.ALREADY_ACCEPTED;
        }

        var effects = scriptRunner.runAccept(template.scriptRef());
        tx.inTransaction((Transaction t) -> progressPort.save(
                t, new QuestProgress(characterId, template.id(), QuestStatus.IN_PROGRESS, effects.progressJson())));
        return AcceptResult.OK;
    }

    public enum AcceptResult { OK, QUEST_NOT_FOUND, ALREADY_ACCEPTED }
}
