package dev.kage.quest.application;

import java.util.ArrayList;
import java.util.List;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/**
 * Xử lý sự kiện "quái chết" cho tiến độ quest — gọi từ {@code ConnectedCharacterRegistry.onMonsterKilled}
 * (server-gateway) cạnh {@code InventoryFacade.grantLoot} đã có, KHÔNG qua {@code EventBus}
 * (engine-ecs) — EventBus tồn tại nhưng chưa dùng ở đâu (1 nguồn duy nhất, chưa tới ngưỡng đáng
 * kích hoạt pub-sub thật, xem feature-quest/CLAUDE.md).
 *
 * <p><b>Tổng quát hoá 2026-09-05.</b> Trước đây lớp này hardcode ĐÚNG MỘT mã quest
 * ({@code kill_training_dummies}) — placeholder có chủ đích, và feature-quest/CLAUDE.md ra điều
 * kiện rõ: <i>"khi có quest thứ 2 THEO DÕI GIẾT QUÁI, bắt buộc tổng quát hoá tra cứu này trước,
 * không copy-paste thêm hằng số hardcode thứ 2"</i>. Quest thứ hai của arc 1 (STORY.md §6 —
 * "đêm trực gặp tán tu cường đạo") chính là lúc đó, nên tổng quát hoá đi TRƯỚC nội dung.
 *
 * <p><b>Java KHÔNG biết quest nào quan tâm quái nào.</b> Mỗi quest đang làm được chạy hook
 * {@code on_event} và chính script tự quyết sự kiện có liên quan không
 * ({@code event.monster_code === "..."}). Đây là Điều 27: Java là cơ chế, Script là nội dung. Nếu
 * Java lọc theo {@code monster_code} thì nội dung quest rò rỉ vào code, và thêm quest sẽ phải sửa
 * Java — đúng thứ kiến trúc script sinh ra để tránh.
 *
 * <p>⚠️ Vẫn CHƯA phải "index quest theo loại sự kiện" (GAMEPLAY.md §9). Phạm vi quét nay bị chặn
 * bởi số quest ĐANG LÀM của một người chơi thay vì một hằng số hardcode — xem
 * {@link QuestProgressPort#listInProgress}.
 */
public final class ProcessMonsterKilledUseCase {

    private static final String MONSTER_KILLED_EVENT_TYPE = "monster_killed";

    private final QuestProgressPort progressPort;
    private final TransactionRunner tx;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    public ProcessMonsterKilledUseCase(QuestProgressPort progressPort, TransactionRunner tx) {
        this.progressPort = progressPort;
        this.tx = tx;
    }

    /**
     * Chạy hook {@code on_event} của mọi quest đang làm cho một lần giết quái.
     *
     * @return những quest VỪA chuyển sang COMPLETED trong chính lần giết này — rỗng nếu không quest
     *     nào đổi trạng thái. Danh sách chứ không phải {@code Optional}: một con quái có thể là mục
     *     tiêu cuối của nhiều quest cùng lúc, và bỏ sót cái thứ hai nghĩa là người chơi không được
     *     báo quest đã xong.
     */
    public List<QuestStatusChange> execute(long characterId, String monsterCode) {
        List<QuestProgressPort.ActiveQuest> active = progressPort.listInProgress(characterId);
        if (active.isEmpty()) {
            return List.of();
        }
        ArrayList<QuestStatusChange> completed = new ArrayList<QuestStatusChange>();
        for (QuestProgressPort.ActiveQuest quest : active) {
            QuestScriptRunner.HookEffects effects = scriptRunner.runEvent(
                    quest.scriptRef(), quest.progressJson(), MONSTER_KILLED_EVENT_TYPE, monsterCode);
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
