package dev.kage.quest.api;

import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.domain.QuestStatus;
import java.util.List;
import java.util.Optional;

/**
 * API công khai của {@code feature-quest} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ đọc bảng {@code quest_template}/{@code character_quest_progress}, chạy
 * {@code ScriptSandbox}, hay thấy kiểu ở {@code application/} trực tiếp (Điều 24 — cùng quy ước
 * {@code InventoryFacade}/{@code ShopFacade}: Facade tự khai báo enum/record kết quả RIÊNG, không
 * lộ type của UseCase ra ngoài).
 */
public final class QuestFacade {

    private final AcceptQuestUseCase acceptQuestUseCase;
    private final ProcessMonsterKilledUseCase processMonsterKilledUseCase;
    private final ClaimQuestRewardUseCase claimQuestRewardUseCase;

    public QuestFacade(
            AcceptQuestUseCase acceptQuestUseCase, ProcessMonsterKilledUseCase processMonsterKilledUseCase,
            ClaimQuestRewardUseCase claimQuestRewardUseCase) {
        this.acceptQuestUseCase = acceptQuestUseCase;
        this.processMonsterKilledUseCase = processMonsterKilledUseCase;
        this.claimQuestRewardUseCase = claimQuestRewardUseCase;
    }

    public AcceptResult acceptQuest(long characterId, String questCode) {
        return switch (acceptQuestUseCase.execute(characterId, questCode)) {
            case OK -> AcceptResult.OK;
            case QUEST_NOT_FOUND -> AcceptResult.QUEST_NOT_FOUND;
            case ALREADY_ACCEPTED -> AcceptResult.ALREADY_ACCEPTED;
        };
    }

    /**
     * Gọi TỪ {@code ConnectedCharacterRegistry.onMonsterKilled} (world tick thread, cạnh
     * {@code InventoryFacade.grantLoot} đã có) — trả {@code Optional} có giá trị CHỈ khi quest VỪA
     * chuyển sang COMPLETED (không push sự kiện cho mỗi lần tiến độ tăng).
     */
    public Optional<QuestStatusChange> onMonsterKilled(long characterId, String monsterCode) {
        return processMonsterKilledUseCase.execute(characterId, monsterCode)
                .map(change -> new QuestStatusChange(change.questCode(), change.status()));
    }

    public ClaimResult claimReward(long characterId, String questCode) {
        ClaimQuestRewardUseCase.ClaimResult result = claimQuestRewardUseCase.execute(characterId, questCode);
        ClaimOutcome outcome = switch (result.outcome()) {
            case OK -> ClaimOutcome.OK;
            case QUEST_NOT_FOUND -> ClaimOutcome.QUEST_NOT_FOUND;
            case NOT_ACCEPTED -> ClaimOutcome.NOT_ACCEPTED;
            case NOT_COMPLETED_YET -> ClaimOutcome.NOT_COMPLETED_YET;
        };
        List<ItemReward> items = result.itemsGranted().stream()
                .map(item -> new ItemReward(item.itemTemplateCode(), item.quantity()))
                .toList();
        return new ClaimResult(outcome, result.expGranted(), items);
    }

    public enum AcceptResult { OK, QUEST_NOT_FOUND, ALREADY_ACCEPTED }

    public enum ClaimOutcome { OK, QUEST_NOT_FOUND, NOT_ACCEPTED, NOT_COMPLETED_YET }

    /** {@code status} chỉ bao giờ là COMPLETED ở lát cắt này (xem {@link #onMonsterKilled} javadoc). */
    public record QuestStatusChange(String questCode, QuestStatus status) {
    }

    public record ItemReward(String itemTemplateCode, int quantity) {
    }

    public record ClaimResult(ClaimOutcome outcome, long expGranted, List<ItemReward> itemsGranted) {
    }
}
