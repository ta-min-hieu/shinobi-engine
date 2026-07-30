package dev.kage.quest.application;

import java.util.Optional;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/**
 * Xử lý sự kiện "quái chết" cho tiến độ quest — gọi từ {@code ConnectedCharacterRegistry.onMonsterKilled}
 * (server-gateway) cạnh {@code InventoryFacade.grantLoot} đã có, KHÔNG qua {@code EventBus}
 * (engine-ecs) — EventBus tồn tại nhưng chưa dùng ở đâu (1 nguồn duy nhất, chưa tới ngưỡng đáng
 * kích hoạt pub-sub thật, xem feature-quest/CLAUDE.md).
 *
 * <p>⚠️ PLACEHOLDER (chỉ 1 quest ở lát cắt này) — kiểm tra ĐÚNG {@link #TRACKED_QUEST_CODE}, chưa
 * quét mọi quest IN_PROGRESS của nhân vật theo loại sự kiện (GAMEPLAY.md §9: "cần index quest theo
 * loại sự kiện, không quét tuyến tính" — đo/tổng quát hoá khi có quest thứ 2 thật).
 */
public final class ProcessMonsterKilledUseCase {

    private static final String TRACKED_QUEST_CODE = "kill_training_dummies";
    private static final String MONSTER_KILLED_EVENT_TYPE = "monster_killed";

    private final QuestProgressPort progressPort;
    private final QuestTemplatePort templatePort;
    private final TransactionRunner tx;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    public ProcessMonsterKilledUseCase(
            QuestProgressPort progressPort, QuestTemplatePort templatePort, TransactionRunner tx) {
        this.progressPort = progressPort;
        this.templatePort = templatePort;
        this.tx = tx;
    }

    /** {@code Optional.empty()} nếu nhân vật không có quest này IN_PROGRESS HOẶC chưa hoàn thành. */
    public Optional<QuestStatusChange> execute(long characterId, String monsterCode) {
        QuestTemplate template = templatePort.findByCode(TRACKED_QUEST_CODE).orElse(null);
        if (template == null) {
            return Optional.empty();
        }
        QuestProgress progress = progressPort.find(characterId, template.id()).orElse(null);
        if (progress == null || progress.status() != QuestStatus.IN_PROGRESS) {
            return Optional.empty();
        }

        QuestScriptRunner.HookEffects effects = scriptRunner.runEvent(
                template.scriptRef(), progress.progressJson(), MONSTER_KILLED_EVENT_TYPE, monsterCode);
        QuestStatus newStatus = effects.completed() ? QuestStatus.COMPLETED : QuestStatus.IN_PROGRESS;
        tx.inTransaction((Transaction t) ->
                progressPort.save(t, new QuestProgress(characterId, template.id(), newStatus, effects.progressJson())));

        if (!effects.completed()) {
            return Optional.empty(); // KHÔNG push sự kiện cho mỗi lần tick tiến độ — chỉ khi hoàn thành.
        }
        return Optional.of(new QuestStatusChange(TRACKED_QUEST_CODE, QuestStatus.COMPLETED));
    }

    public record QuestStatusChange(String questCode, QuestStatus status) {}
}
