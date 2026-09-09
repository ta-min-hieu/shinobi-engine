package dev.kage.quest.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private static final String MONSTER_KILLED_EVENT_TYPE = "monster_killed";
    private static final String NPC_TALKED_EVENT_TYPE = "npc_talked";

    private final QuestProgressPort progressPort;
    private final TransactionRunner tx;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    public ProcessQuestEventUseCase(QuestProgressPort progressPort, TransactionRunner tx) {
        this.progressPort = progressPort;
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
     * @return như {@link #monsterKilled}
     */
    public List<QuestStatusChange> npcTalked(long characterId, String npcCode) {
        return dispatch(characterId, Map.<String, Object>of("type", NPC_TALKED_EVENT_TYPE, "npc_code", npcCode));
    }

    private List<QuestStatusChange> dispatch(long characterId, Map<String, Object> event) {
        List<QuestProgressPort.ActiveQuest> active = progressPort.listInProgress(characterId);
        if (active.isEmpty()) {
            return List.of();
        }
        ArrayList<QuestStatusChange> completed = new ArrayList<QuestStatusChange>();
        for (QuestProgressPort.ActiveQuest quest : active) {
            QuestScriptRunner.HookEffects effects =
                    scriptRunner.runEvent(quest.scriptRef(), quest.progressJson(), event);
            if (!effects.completed() && effects.progressJson().equals(quest.progressJson())) {
                // Sự kiện này không liên quan tới quest này — script trả lại đúng chuỗi tiến độ cũ.
                // Bỏ qua hẳn lần ghi thay vì ghi đè giá trị y hệt: từ khi có LOẠI sự kiện thứ hai,
                // mỗi quest đang làm nhận MỌI sự kiện, nên phần lớn lần chạy rơi vào nhánh này.
                continue;
            }
            QuestStatus newStatus = effects.completed() ? QuestStatus.COMPLETED : QuestStatus.IN_PROGRESS;
            tx.inTransaction((Transaction t) -> progressPort.save(
                    t, new QuestProgress(characterId, quest.questTemplateId(), newStatus, effects.progressJson())));
            if (effects.completed()) {
                // Chỉ báo khi HOÀN THÀNH — không push sự kiện cho mỗi lần tiến độ tăng.
                completed.add(new QuestStatusChange(quest.questCode(), QuestStatus.COMPLETED));
            }
        }
        return List.copyOf(completed);
    }

    public record QuestStatusChange(String questCode, QuestStatus status) {}
}
