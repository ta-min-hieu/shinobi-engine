package dev.kage.quest.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import dev.kage.quest.application.AbandonQuestUseCase;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
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
    private final ProcessQuestEventUseCase processQuestEventUseCase;
    private final ClaimQuestRewardUseCase claimQuestRewardUseCase;
    private final ListQuestsUseCase listQuestsUseCase;
    private final AbandonQuestUseCase abandonQuestUseCase;
    private final ConcurrentHashMap<Long, ReentrantLock> eventLocksByCharacterId = new ConcurrentHashMap<>();

    /** Dựng ở composition root. */
    public QuestFacade(
            AcceptQuestUseCase acceptQuestUseCase,
            ProcessQuestEventUseCase processQuestEventUseCase,
            ClaimQuestRewardUseCase claimQuestRewardUseCase,
            QuestLogUseCases questLogUseCases) {
        this.acceptQuestUseCase = acceptQuestUseCase;
        this.processQuestEventUseCase = processQuestEventUseCase;
        this.claimQuestRewardUseCase = claimQuestRewardUseCase;
        this.listQuestsUseCase = questLogUseCases.list();
        this.abandonQuestUseCase = questLogUseCases.abandon();
    }

    /** Nhận quest {@code questCode} — kiểm quest tiên quyết (ADR-044) và chạy hook {@code on_accept}. */
    public AcceptResult acceptQuest(long characterId, String questCode) {
        return switch (acceptQuestUseCase.execute(characterId, questCode)) {
            case OK -> AcceptResult.OK;
            case QUEST_NOT_FOUND -> AcceptResult.QUEST_NOT_FOUND;
            case ALREADY_ACCEPTED -> AcceptResult.ALREADY_ACCEPTED;
            case PREREQUISITE_NOT_MET -> AcceptResult.PREREQUISITE_NOT_MET;
            case SCRIPT_ERROR -> AcceptResult.SCRIPT_ERROR;
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
        return withEventLock(
                characterId, () -> toStatusChanges(processQuestEventUseCase.monsterKilled(characterId, monsterCode)));
    }

    /**
     * Xếp hàng mọi SỰ KIỆN quest của cùng một nhân vật (sửa 2026-10-06).
     *
     * <p><b>Vì sao.</b> Gateway đẩy mỗi con quái chết sang một Virtual Thread riêng, nên một chiêu diện
     * rộng hạ nhiều con cùng nhịp là nhiều lời gọi chồng nhau. {@code ProcessQuestEventUseCase} ghi
     * bằng compare-and-set và chỉ thử lại {@code MAX_WRITE_ATTEMPTS} = 3 lần, với giả định "ba lần
     * xung đột liền là có gì sai, không phải tải cao". Giả định đó SAI: N lời gọi cùng xuất phát thì
     * kẻ về chót thua tới N−1 lần dù mọi thứ đều đúng. Đo thật trên Postgres
     * ({@code QuestEventConcurrencyIntegrationTest}): 8 cú hạ cùng lúc chỉ được đếm <b>3</b> — năm cú
     * biến mất khỏi tiến độ quest, không báo lỗi gì ngoài một dòng WARN.
     *
     * <p><b>Cách sửa.</b> Cùng mẫu khoá/nhân vật của {@code InventoryFacade}: trong một tiến trình
     * gateway, sự kiện của một nhân vật chạy lần lượt, nên compare-and-set không còn thua vì chính
     * người chơi đó. Compare-and-set vẫn ở nguyên chỗ cũ — nó là lớp gác khi có hơn một tiến trình,
     * và là thứ giữ "giao đồ đúng một lần". {@link ReentrantLock} chứ không {@code synchronized}: gọi
     * từ Virtual Thread, chờ khoá không được ghim carrier thread. Map không bao giờ dọn — cùng lý lẽ
     * ở {@code InventoryFacade}: số nhân vật hữu hạn, một khoá rỗng nặng vài chục byte.
     *
     * <p>Nhận/lĩnh quest KHÔNG đi qua khoá này: chúng không đọc-rồi-ghi tiến độ bằng script, và đã có
     * trọng tài riêng trong DB ({@code insertIfAbsent}, {@code markClaimedIfCompleted}).
     */
    private <T> T withEventLock(long characterId, Supplier<T> event) {
        ReentrantLock lock = eventLocksByCharacterId.computeIfAbsent(characterId, id -> new ReentrantLock());
        lock.lock();
        try {
            return event.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gọi TỪ {@code GatewayChannelHandler.handleTalkToNpcBlocking} (authExecutor, cạnh
     * {@code DialogueFacade.startDialogue} đã có) — mục tiêu quest loại THỨ HAI của dự án
     * ("đi gặp X"), thêm 2026-09-09 cho nhịp mở arc 2.
     *
     * <p>⚠️ KHÔNG thêm opcode nào cho việc này. Lệnh vào đã có sẵn ({@code TalkToNpcCommand} 601, từ
     * Phase 6) và đường báo ra cũng vậy ({@code QuestStatusChangedEvent} 612) — cái thiếu chỉ là
     * nhánh nối hai đầu đó qua feature-quest. Đúng bài học đã trả giá ở ADR-046: hỏi "client ĐÃ có
     * đường nào nhận thứ này chưa" TRƯỚC khi kết luận có phải đổi protocol hay không.
     *
     * <p>Cũng mang mục tiêu loại THỨ BA ("mang vật X tới NPC Y", 2026-09-11): script gọi
     * {@code ctx.take_item} khi gặp đúng người, và đồ bị lấy khỏi túi trong cùng transaction với lần
     * ghi tiến độ. Vẫn không có opcode mới — cửa sổ túi đồ tự hỏi lại {@code ListInventoryRequest}
     * mỗi lần mở.
     *
     * <p>Trả cùng dạng {@link #onMonsterKilled}: chỉ những quest VỪA chuyển sang COMPLETED.
     */
    public List<QuestStatusChange> onNpcTalked(long characterId, String npcCode) {
        return withEventLock(
                characterId, () -> toStatusChanges(processQuestEventUseCase.npcTalked(characterId, npcCode)));
    }

    /**
     * Bỏ quest đang làm (ADR-054) — xoá tiến độ, nhận lại được từ đầu. Đi qua CÙNG khoá sự kiện của
     * nhân vật: không chen giữa một lần {@code on_event} đang chạy (lần ghi compare-and-set tới sau
     * thua vì dòng đã mất — đúng ý).
     */
    public AbandonResult abandonQuest(long characterId, String questCode) {
        return withEventLock(characterId, () -> switch (abandonQuestUseCase.execute(characterId, questCode)) {
            case OK -> AbandonResult.OK;
            case QUEST_NOT_FOUND -> AbandonResult.QUEST_NOT_FOUND;
            case NOT_IN_PROGRESS -> AbandonResult.NOT_IN_PROGRESS;
        });
    }

    private static List<QuestStatusChange> toStatusChanges(List<ProcessQuestEventUseCase.QuestStatusChange> changes) {
        return changes.stream()
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
                    case SCRIPT_ERROR -> ClaimOutcome.SCRIPT_ERROR;
                    case INVENTORY_FULL -> ClaimOutcome.INVENTORY_FULL;
                };
        List<ItemReward> items = result.itemsGranted().stream()
                .map(item -> new ItemReward(item.itemTemplateCode(), item.quantity()))
                .toList();
        return new ClaimResult(outcome, result.expGranted(), result.goldGranted(), items);
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

    /**
     * Tiến độ ĐỤC ({@code progress_json}) của các quest đang làm, theo mã quest — chỉ để script hội
     * thoại đọc (gateway {@code CharacterDialogueFacts}). Quest không đang làm thì không có khoá.
     */
    public Map<String, String> inProgressJson(long characterId) {
        return listQuestsUseCase.inProgressJson(characterId);
    }

    /** Kết quả bỏ quest (ADR-054). */
    public enum AbandonResult {
        OK,
        QUEST_NOT_FOUND,
        /** Chưa nhận, đã hoàn thành (chờ lĩnh), hoặc đã lĩnh. */
        NOT_IN_PROGRESS
    }

    /** Kết quả nhận quest. */
    public enum AcceptResult {
        OK,
        QUEST_NOT_FOUND,
        ALREADY_ACCEPTED,
        /** ADR-044 — quest tiên quyết chưa lĩnh thưởng xong. */
        PREREQUISITE_NOT_MET,
        /** Hook {@code on_accept} của script quest ném lỗi — không ghi gì xuống DB. */
        SCRIPT_ERROR
    }

    /** Kết quả lĩnh thưởng quest. */
    public enum ClaimOutcome {
        OK,
        QUEST_NOT_FOUND,
        NOT_ACCEPTED,
        NOT_COMPLETED_YET,
        DUPLICATE_REQUEST,
        /** Hook {@code on_claim} ném lỗi — chưa trao gì, quest vẫn COMPLETED và lĩnh lại được. */
        SCRIPT_ERROR,
        /** Túi không đủ ô — chưa trao gì, quest vẫn COMPLETED và lĩnh lại được sau khi dọn túi. */
        INVENTORY_FULL
    }

    /** {@code status} chỉ bao giờ là COMPLETED ở lát cắt này (xem {@link #onMonsterKilled} javadoc). */
    public record QuestStatusChange(String questCode, QuestStatus status) {}

    /** Một món thưởng đã trao. */
    public record ItemReward(String itemTemplateCode, int quantity) {}

    /** Kết quả lĩnh thưởng — EXP và vật phẩm chỉ khác rỗng khi {@code outcome == OK}. */
    public record ClaimResult(ClaimOutcome outcome, long expGranted, long goldGranted, List<ItemReward> itemsGranted) {
        /** Chép phòng thủ danh sách vật phẩm. */
        public ClaimResult {
            itemsGranted = List.copyOf(itemsGranted);
        }
    }
}
