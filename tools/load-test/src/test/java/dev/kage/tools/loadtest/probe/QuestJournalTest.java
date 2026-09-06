package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.QuestLogEntry;
import dev.kage.protocol.QuestStatus;

/** Bảng nhật ký nhiệm vụ + chênh lệch hai lần đọc — thuần logic, không cần server. */
final class QuestJournalTest {

    @Test
    void shouldSayJournalIsEmpty_whenCharacterHasNoQuest() {
        assertThat(QuestJournal.renderTable(List.of())).contains("nhật ký trống");
    }

    /** Thứ tự theo mã quest, không theo thứ tự server trả — hai lần chạy phải so sánh được với nhau. */
    @Test
    void shouldRenderRowsSortedByQuestCode() {
        String table = QuestJournal.renderTable(List.of(
                entry("night_watch_bandit", QuestStatus.QUEST_STATUS_IN_PROGRESS),
                entry("clear_old_archive", QuestStatus.QUEST_STATUS_CLAIMED)));

        assertThat(table.indexOf("clear_old_archive")).isLessThan(table.indexOf("night_watch_bandit"));
        assertThat(table).contains("CLAIMED").contains("IN_PROGRESS").doesNotContain("QUEST_STATUS_");
    }

    @Test
    void shouldReportNoChange_whenBothReadsAreIdentical() {
        List<QuestLogEntry> same = List.of(entry("kill_training_dummies", QuestStatus.QUEST_STATUS_IN_PROGRESS));

        assertThat(QuestJournal.between(same, same)).isEmpty();
        assertThat(QuestJournal.renderChanges(List.of())).contains("không quest nào đổi trạng thái");
    }

    /** Nhận quest mới = dòng chưa từng có ở lần đọc trước, đúng thứ mà kịch bản nhận nhiệm vụ tạo ra. */
    @Test
    void shouldReportNewlyAcceptedQuest_asAbsentToInProgress() {
        List<QuestJournal.StatusChange> changes = QuestJournal.between(
                List.of(), List.of(entry("night_watch_bandit", QuestStatus.QUEST_STATUS_IN_PROGRESS)));

        assertThat(changes)
                .containsExactly(
                        new QuestJournal.StatusChange("night_watch_bandit", QuestJournal.ABSENT, "IN_PROGRESS"));
    }

    @Test
    void shouldReportClaimedQuest_asCompletedToClaimed() {
        List<QuestJournal.StatusChange> changes = QuestJournal.between(
                List.of(
                        entry("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED),
                        entry("night_watch_bandit", QuestStatus.QUEST_STATUS_IN_PROGRESS)),
                List.of(
                        entry("kill_training_dummies", QuestStatus.QUEST_STATUS_CLAIMED),
                        entry("night_watch_bandit", QuestStatus.QUEST_STATUS_IN_PROGRESS)));

        assertThat(changes)
                .containsExactly(new QuestJournal.StatusChange("kill_training_dummies", "COMPLETED", "CLAIMED"));
        assertThat(QuestJournal.renderChanges(changes)).contains("COMPLETED -> CLAIMED");
    }

    private static QuestLogEntry entry(String questCode, QuestStatus status) {
        return QuestLogEntry.newBuilder()
                .setQuestCode(questCode)
                .setStatus(status)
                .build();
    }
}
