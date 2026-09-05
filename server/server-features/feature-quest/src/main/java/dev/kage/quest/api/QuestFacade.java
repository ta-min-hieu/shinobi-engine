package dev.kage.quest.api;

import java.util.List;

import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestStatus;

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
    private final ListQuestsUseCase listQuestsUseCase;

    public QuestFacade(
            AcceptQuestUseCase acceptQuestUseCase,
            ProcessMonsterKilledUseCase processMonsterKilledUseCase,
            ClaimQuestRewardUseCase claimQuestRewardUseCase,
            ListQuestsUseCase listQuestsUseCase) {
        this.acceptQuestUseCase = acceptQuestUseCase;
        this.processMonsterKilledUseCase = processMonsterKilledUseCase;
        this.claimQuestRewardUseCase = claimQuestRewardUseCase;
        this.listQuestsUseCase = listQuestsUseCase;
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
     * {@code InventoryFacade.grantLoot} đã có) — chỉ trả những quest VỪA chuyển sang COMPLETED
     * (không push sự kiện cho mỗi lần tiến độ tăng).
     *
     * <p>Trả DANH SÁCH thay vì {@code Optional} từ 2026-09-05: một con quái có thể là mục tiêu cuối
     * của nhiều quest cùng lúc kể từ khi có quest thứ hai theo dõi giết quái.
     */
    public List<QuestStatusChange> onMonsterKilled(long characterId, String monsterCode) {
        return processMonsterKilledUseCase.execute(characterId, monsterCode).stream()
                .map(change -> new QuestStatusChange(change.questCode(), change.status()))
                .toList();
    }

    /** {@code idempotencyKey} (ADR-025) — client sinh UUID, giữ nguyên khi tự retry. */
    public ClaimResult claimReward(long characterId, String questCode, String idempotencyKey) {
        ClaimQuestRewardUseCase.ClaimResult result =
                claimQuestRewardUseCase.execute(characterId, questCode, idempotencyKey);
        ClaimOutcome outcome =
                switch (result.outcome()) {
                    case OK -> ClaimOutcome.OK;
                    case QUEST_NOT_FOUND -> ClaimOutcome.QUEST_NOT_FOUND;
                    case NOT_ACCEPTED -> ClaimOutcome.NOT_ACCEPTED;
                    case NOT_COMPLETED_YET -> ClaimOutcome.NOT_COMPLETED_YET;
                    case DUPLICATE_REQUEST -> ClaimOutcome.DUPLICATE_REQUEST;
                };
        List<ItemReward> items = result.itemsGranted().stream()
                .map(item -> new ItemReward(item.itemTemplateCode(), item.quantity()))
                .toList();
        return new ClaimResult(outcome, result.expGranted(), items);
    }

    /**
     * Nhật ký nhiệm vụ (ADR-035 Decision-4) — GỒM CẢ quest đã CLAIMED.
     *
     * <p>Trả thẳng {@link QuestLogEntry} của {@code domain/} thay vì khai báo record riêng ở Facade
     * như {@link QuestStatusChange}: record đó CHỈ để không lộ type của {@code application/} ra
     * ngoài, còn kiểu {@code domain/} thì Facade này vốn đã lộ sẵn ({@link QuestStatus} nằm trong
     * chính {@link QuestStatusChange}). Thêm một bản sao y hệt chỉ để đổi tên package là trùng lặp thuần.
     */
    public List<QuestLogEntry> listQuests(long characterId) {
        return listQuestsUseCase.execute(characterId);
    }

    public enum AcceptResult {
        OK,
        QUEST_NOT_FOUND,
        ALREADY_ACCEPTED
    }

    public enum ClaimOutcome {
        OK,
        QUEST_NOT_FOUND,
        NOT_ACCEPTED,
        NOT_COMPLETED_YET,
        DUPLICATE_REQUEST
    }

    /** {@code status} chỉ bao giờ là COMPLETED ở lát cắt này (xem {@link #onMonsterKilled} javadoc). */
    public record QuestStatusChange(String questCode, QuestStatus status) {}

    public record ItemReward(String itemTemplateCode, int quantity) {}

    public record ClaimResult(ClaimOutcome outcome, long expGranted, List<ItemReward> itemsGranted) {
        public ClaimResult {
            itemsGranted = List.copyOf(itemsGranted);
        }
    }
}
