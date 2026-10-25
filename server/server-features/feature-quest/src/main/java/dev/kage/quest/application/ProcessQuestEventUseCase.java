package dev.kage.quest.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.QuestItemTakePort;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/**
 * Đưa MỘT sự kiện thế giới vào tiến độ mọi quest đang làm của một nhân vật — gọi từ server-gateway
 * ({@code ConnectedCharacterRegistry.onMonsterKilled} cho quái chết,
 * {@code GatewayChannelHandler.handleTalkToNpcBlocking} cho nói chuyện NPC), KHÔNG qua
 * {@code EventBus} (engine-ecs) — EventBus tồn tại nhưng vẫn chưa dùng ở đâu, xem feature-quest/CLAUDE.md.
 *
 * <p><b>Tổng quát hoá lần thứ hai (2026-09-09).</b> Lớp này trước tên là
 * {@code ProcessMonsterKilledUseCase}. Lần một (2026-09-05) gỡ hằng số mã quest hardcode khi có
 * quest thứ HAI theo dõi giết quái. Lần này gỡ nốt giả định còn lại — rằng chỉ tồn tại MỘT loại sự
 * kiện — khi có mục tiêu quest loại thứ HAI ({@code npc_talked}, nhịp mở arc 2). Cùng một luật đã
 * áp cho lần một: <i>tổng quát hoá đi TRƯỚC nội dung, không copy-paste thêm một bản sao thứ hai của
 * chính vòng lặp này</i>. Bản sao đó sẽ là chỗ hai đường xử lý âm thầm trôi khỏi nhau.
 *
 * <p><b>Mục tiêu loại THỨ BA — "mang vật X tới NPC Y" (2026-09-11).</b> KHÔNG thêm lối vào hay loại
 * sự kiện nào: nó đi trên chính sự kiện {@code npc_talked}, cộng MỘT hiệu ứng mới —
 * {@code ctx.take_item(code, qty)}. Lấy đồ là ĐIỀU KIỆN của cả lần chạy hook: tiến độ mới và mọi lần
 * lấy đồ ghi trong CÙNG một transaction; thiếu một món thì rollback hết (xem {@link #write}). Kiểm
 * túi lúc giao nghĩa là chỉ nhìn TRẠNG THÁI, không nhìn đồ tới bằng đường nào — nên không phải móc
 * vào năm đường cấp vật phẩm (rơi đồ, cửa hàng, thưởng quest, hòm thư, giao dịch).
 *
 * <p><b>Java KHÔNG biết quest nào quan tâm sự kiện nào</b> — kể cả LOẠI sự kiện. Mỗi quest đang làm
 * đều được chạy hook {@code on_event} và chính script tự quyết ({@code event.type === "npc_talked"
 * && event.npc_code === "..."}). Đây là Điều 27: Java là cơ chế, Script là nội dung. Java mà lọc
 * theo loại sự kiện thì nội dung quest rò rỉ vào code, và thêm quest sẽ phải sửa Java — đúng thứ
 * kiến trúc script sinh ra để tránh.
 *
 * <p>⚠️ Vẫn CHƯA phải "index quest theo loại sự kiện" (GAMEPLAY.md §9). Phạm vi quét bị chặn bởi số
 * quest ĐANG LÀM của một người chơi — xem {@link QuestProgressPort#listInProgress}.
 */
public final class ProcessQuestEventUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessQuestEventUseCase.class);

    private static final String MONSTER_KILLED_EVENT_TYPE = "monster_killed";
    private static final String NPC_TALKED_EVENT_TYPE = "npc_talked";
    private static final String ESCORT_ARRIVED_EVENT_TYPE = "escort_arrived";
    private static final String ESCORT_FAILED_EVENT_TYPE = "escort_failed";

    /** {@code audit_log.action} cho mỗi lần một quest lấy đồ khỏi túi — cạnh {@code QUEST_CLAIM}. */
    static final String ITEM_TAKEN_AUDIT_ACTION = "QUEST_ITEM_TAKEN";

    /**
     * Số lần chạy lại hook khi một sự kiện KHÁC của cùng nhân vật đã ghi tiến độ giữa lúc đọc và lúc
     * ghi ({@link QuestProgressPort#saveIfUnchanged} trả {@code false}). Xung đột cần hai sự kiện của
     * CÙNG một người rơi vào CÙNG một quest trong vài mili-giây; ba lần liền là dấu hiệu có gì đó
     * sai chứ không phải tải cao — khi đó bỏ sự kiện (không ghi, không lấy đồ) và ghi log.
     *
     * <p>⚠️ Câu "ba lần liền là có gì sai" CHỈ đúng khi sự kiện của một nhân vật không chen nhau
     * trong cùng tiến trình: N lời gọi cùng xuất phát thì kẻ về chót thua tới N−1 lần dù mọi thứ
     * đều đúng (đo 2026-10-06: 8 cú hạ cùng lúc chỉ đếm được 3). Điều kiện đó do
     * {@code QuestFacade.withEventLock} bảo đảm — gọi thẳng lớp này từ nhiều luồng cho cùng một
     * nhân vật là mất sự kiện.
     */
    private static final int MAX_WRITE_ATTEMPTS = 3;

    private final QuestProgressPort progressPort;
    private final QuestItemTakePort itemTakePort;
    private final AuditPort auditPort;
    private final TransactionRunner tx;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    public ProcessQuestEventUseCase(
            QuestProgressPort progressPort, QuestItemTakePort itemTakePort, AuditPort auditPort, TransactionRunner tx) {
        this.progressPort = progressPort;
        this.itemTakePort = itemTakePort;
        this.auditPort = auditPort;
        this.tx = tx;
    }

    /**
     * Một con quái vừa chết dưới tay nhân vật.
     *
     * @return những quest VỪA chuyển sang COMPLETED trong chính lần này — rỗng nếu không quest nào
     *     đổi trạng thái. Danh sách chứ không phải {@code Optional}: một con quái có thể là mục tiêu
     *     cuối của nhiều quest cùng lúc, và bỏ sót cái thứ hai nghĩa là người chơi không được báo
     *     quest đã xong.
     */
    public List<QuestStatusChange> monsterKilled(long characterId, String monsterCode) {
        return dispatch(
                characterId, Map.<String, Object>of("type", MONSTER_KILLED_EVENT_TYPE, "monster_code", monsterCode));
    }

    /**
     * Nhân vật vừa mở lời với một NPC ({@code TalkToNpcCommand}).
     *
     * <p>⚠️ Bắn ở lúc MỞ hội thoại, không phải lúc chọn một lựa chọn cụ thể: mục tiêu "đi gặp X" đã
     * hoàn thành ngay khi người chơi đứng trước X, và buộc họ bấm đúng một nhánh thoại thì mục tiêu
     * thật sự trở thành "bấm đúng nút", không phải "tìm ra người". Sự kiện vì thế lặp lại mỗi lần
     * nói chuyện — script phải tự chịu được điều đó (đặt cờ, đừng cộng dồn).
     *
     * <p>Cũng là sự kiện mang mục tiêu "mang vật X tới NPC Y": script gọi {@code ctx.take_item} khi
     * gặp đúng người; người chơi chưa mang đủ đồ thì lần mở lời đó không để lại dấu vết gì.
     *
     * @return như {@link #monsterKilled}
     */
    public List<QuestStatusChange> npcTalked(long characterId, String npcCode) {
        return dispatch(characterId, Map.<String, Object>of("type", NPC_TALKED_EVENT_TYPE, "npc_code", npcCode));
    }

    /**
     * ADR-114 D3 — đoàn hộ tống của quest {@code questCode} vừa tới nơi ({@code arrived}) hoặc thất bại. Sự kiện mang
     * {@code quest_code}; script tự kiểm đúng quest của nó (Điều 27), như {@code npc_code} của {@link #npcTalked}.
     *
     * @return như {@link #monsterKilled}
     */
    public List<QuestStatusChange> escortFinished(long characterId, String questCode, boolean arrived) {
        return dispatch(
                characterId,
                Map.<String, Object>of(
                        "type",
                        arrived ? ESCORT_ARRIVED_EVENT_TYPE : ESCORT_FAILED_EVENT_TYPE,
                        "quest_code",
                        questCode));
    }

    private List<QuestStatusChange> dispatch(long characterId, Map<String, Object> event) {
        List<QuestProgressPort.ActiveQuest> active = progressPort.listInProgress(characterId);
        if (active.isEmpty()) {
            return List.of();
        }
        ArrayList<QuestStatusChange> completed = new ArrayList<QuestStatusChange>();
        for (QuestProgressPort.ActiveQuest quest : active) {
            if (applyEvent(characterId, quest, event)) {
                // Chỉ báo khi HOÀN THÀNH — không push sự kiện cho mỗi lần tiến độ tăng.
                completed.add(new QuestStatusChange(quest.questCode(), QuestStatus.COMPLETED));
            }
        }
        return List.copyOf(completed);
    }

    /**
     * Chạy hook cho MỘT quest rồi ghi kết quả; xung đột thì đọc lại và chạy lại trên tiến độ mới.
     *
     * @return {@code true} nếu lần này đã GHI quest xuống trạng thái COMPLETED
     */
    private boolean applyEvent(long characterId, QuestProgressPort.ActiveQuest quest, Map<String, Object> event) {
        String observed = quest.progressJson();
        for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
            QuestScriptRunner.HookEffects effects = scriptRunner.runEvent(quest.scriptRef(), observed, event);
            if (effects.scriptFailed()) {
                // Hiệu ứng DỞ DANG (có thể đã kịp gọi take_item/save_progress trước chỗ lỗi) — bỏ hết.
                return false;
            }
            if (!effects.completed() && effects.progressJson().equals(observed)) {
                // Sự kiện này không liên quan tới quest này — script trả lại đúng chuỗi tiến độ cũ.
                // Bỏ qua hẳn lần ghi thay vì ghi đè giá trị y hệt: từ khi có LOẠI sự kiện thứ hai,
                // mỗi quest đang làm nhận MỌI sự kiện, nên phần lớn lần chạy rơi vào nhánh này.
                // Hệ quả có chủ đích: take_item mà tiến độ không đổi thì cũng KHÔNG lấy đồ.
                return false;
            }
            WriteOutcome outcome = write(characterId, quest.questTemplateId(), observed, effects);
            if (outcome == WriteOutcome.WRITTEN) {
                return effects.completed();
            }
            if (outcome == WriteOutcome.ITEMS_MISSING) {
                return false;
            }
            Optional<QuestProgress> fresh = progressPort.find(characterId, quest.questTemplateId());
            if (fresh.isEmpty() || fresh.get().status() != QuestStatus.IN_PROGRESS) {
                // Sự kiện kia đã đóng quest này (vd một lần giao đồ song song đã xong trước) — không
                // còn gì để làm, và tuyệt đối không được lấy đồ lần thứ hai.
                return false;
            }
            observed = fresh.get().progressJson();
        }
        LOG.warn(
                "Bỏ sự kiện quest sau {} lần xung đột ghi liên tiếp: characterId={}, questTemplateId={}",
                MAX_WRITE_ATTEMPTS,
                characterId,
                quest.questTemplateId());
        return false;
    }

    /**
     * Ghi tiến độ + lấy đồ + audit trong MỘT transaction.
     *
     * <p>Thứ tự có chủ đích: compare-and-set tiến độ ĐẦU TIÊN. Trên Postgres, câu {@code UPDATE} đó
     * khoá dòng tiến độ — một lần giao đồ song song của cùng người chơi phải chờ lần này commit, rồi
     * thấy {@code progress_json} đã khác và nhận {@code 0} dòng. Đó là chỗ duy nhất ngăn đồ bị trừ
     * hai lần cho một lần giao. Xung đột thì chưa có gì bị ghi, transaction commit rỗng.
     */
    private WriteOutcome write(
            long characterId,
            long questTemplateId,
            String expectedProgressJson,
            QuestScriptRunner.HookEffects effects) {
        QuestStatus newStatus = effects.completed() ? QuestStatus.COMPLETED : QuestStatus.IN_PROGRESS;
        QuestProgress next = new QuestProgress(characterId, questTemplateId, newStatus, effects.progressJson());
        AtomicBoolean written = new AtomicBoolean(false);
        try {
            tx.inTransaction((Transaction t) -> {
                if (!effects.itemTakes().isEmpty()) {
                    // Có lấy đồ ⇒ khoá túi đồ ĐẦU TIÊN, trước dòng tiến độ — thứ tự khoá chung
                    // (DATABASE.md §5.1). Không lấy đồ thì không đụng túi, khỏi xếp hàng.
                    itemTakePort.lockInventory(t, characterId);
                }
                if (!progressPort.saveIfUnchanged(t, next, expectedProgressJson)) {
                    return;
                }
                for (QuestScriptRunner.ItemTake take : effects.itemTakes()) {
                    takeOrRollback(t, characterId, questTemplateId, take);
                }
                written.set(true);
            });
        } catch (ItemsMissingException e) {
            // Transaction đã rollback: lần ghi tiến độ và mọi lần lấy đồ trước món thiếu đều bị huỷ.
            return WriteOutcome.ITEMS_MISSING;
        }
        return written.get() ? WriteOutcome.WRITTEN : WriteOutcome.CONFLICT;
    }

    private void takeOrRollback(
            Transaction t, long characterId, long questTemplateId, QuestScriptRunner.ItemTake take) {
        if (!itemTakePort.takeItemByCode(t, characterId, take.itemTemplateCode(), take.quantity())) {
            throw new ItemsMissingException();
        }
        auditPort.log(t, characterId, ITEM_TAKEN_AUDIT_ACTION, itemTakenDetailJson(questTemplateId, take));
    }

    private static String itemTakenDetailJson(long questTemplateId, QuestScriptRunner.ItemTake take) {
        return "{\"questTemplateId\":%d,\"itemCode\":\"%s\",\"quantity\":%d}"
                .formatted(questTemplateId, take.itemTemplateCode(), take.quantity());
    }

    private enum WriteOutcome {
        WRITTEN,
        /** Một sự kiện khác đã ghi tiến độ trước — chưa ghi gì, đọc lại rồi chạy lại hook. */
        CONFLICT,
        /** Người chơi không mang đủ đồ — đã rollback, sự kiện coi như không xảy ra với quest này. */
        ITEMS_MISSING
    }

    /**
     * Cách DUY NHẤT để bảo {@link TransactionRunner} rollback mà không phải lỗi thật: runner commit
     * khi khối lệnh chạy xong, rollback khi nó ném. Không bao giờ thoát ra khỏi {@link #write}.
     */
    private static final class ItemsMissingException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ItemsMissingException() {
            super("không đủ vật phẩm để giao", null, false, false);
        }
    }

    public record QuestStatusChange(String questCode, QuestStatus status) {}
}
