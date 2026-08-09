package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.QuestStatus;

/** {@link QuestTracker} thuần Java, không GL — test thẳng như {@link DialogueControllerTest}. */
class QuestTrackerTest {

    private final QuestTracker tracker = new QuestTracker();

    @Test
    void startsWithNothingTracked() {
        assertThat(tracker.isTracking()).isFalse();
        assertThat(tracker.isClaimable()).isFalse();
    }

    @Test
    void tracksAnAcceptedQuestAsInProgress() {
        tracker.onAccepted("kill_training_dummies");

        assertThat(tracker.isTracking()).isTrue();
        assertThat(tracker.questCode()).isEqualTo("kill_training_dummies");
        assertThat(tracker.status()).isEqualTo(QuestStatus.QUEST_STATUS_IN_PROGRESS);
        assertThat(tracker.isClaimable()).isFalse(); // chưa xong thì chưa lĩnh được
    }

    @Test
    void becomesClaimableOnCompletion() {
        tracker.onAccepted("kill_training_dummies");

        tracker.onStatusChanged("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED);

        assertThat(tracker.isClaimable()).isTrue();
    }

    @Test
    void ignoresEventsForAQuestItIsNotTracking() {
        // Người chơi có thể đang giữ nhiều nhiệm vụ phía server mà client không có cách nào biết
        // (quest.proto không có opcode liệt kê) — sự kiện của nhiệm vụ khác không được ghi đè.
        tracker.onAccepted("kill_training_dummies");

        tracker.onStatusChanged("some_other_quest", QuestStatus.QUEST_STATUS_COMPLETED);

        assertThat(tracker.isClaimable()).isFalse();
        assertThat(tracker.questCode()).isEqualTo("kill_training_dummies");
    }

    @Test
    void keepsTheSameIdempotencyKeyAcrossRetriesOfOneClaim() {
        // ADR-025: sinh khoá mới mỗi lần bấm sẽ biến chính cơ chế chống trùng thành vô dụng —
        // server chỉ chặn được lệnh lặp khi thấy lại ĐÚNG khoá cũ.
        tracker.onAccepted("kill_training_dummies");
        tracker.onStatusChanged("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED);

        String first = tracker.claimKey();

        assertThat(tracker.claimKey()).isEqualTo(first);
        assertThat(first).isNotBlank();
    }

    @Test
    void stopsTrackingOnceTheRewardIsClaimed() {
        tracker.onAccepted("kill_training_dummies");
        tracker.onStatusChanged("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED);
        tracker.claimKey();

        tracker.onClaimSettled(true);

        assertThat(tracker.isTracking()).isFalse();
        assertThat(tracker.isClaimable()).isFalse();
        assertThat(tracker.status()).isEqualTo(QuestStatus.QUEST_STATUS_CLAIMED);
    }

    @Test
    void staysClaimableAfterAFailedClaimButIssuesAFreshKey() {
        tracker.onAccepted("kill_training_dummies");
        tracker.onStatusChanged("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED);
        String failedKey = tracker.claimKey();

        tracker.onClaimSettled(false);

        assertThat(tracker.isClaimable()).isTrue(); // bấm lại được
        // Lệnh CŨ đã có kết luận rồi — lần bấm sau là một lần nhận MỚI, không phải retry của nó.
        assertThat(tracker.claimKey()).isNotEqualTo(failedKey);
    }

    @Test
    void acceptingAnotherQuestReplacesTheTrackedOne() {
        tracker.onAccepted("kill_training_dummies");
        tracker.onStatusChanged("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED);

        tracker.onAccepted("another_quest");

        assertThat(tracker.questCode()).isEqualTo("another_quest");
        assertThat(tracker.isClaimable()).isFalse();
    }
}
