package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.QuestLogEntry;
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

    // ---------------------------------------------------------------------------------------------
    // ADR-035 (opcode 610/611) — nhật ký đầy đủ, và đúng bug nó vá: "nhận nhiệm vụ → relog → mất
    // thưởng vĩnh viễn".
    // ---------------------------------------------------------------------------------------------

    private static ListQuestsResponse listQuestsResponse(String questCode, QuestStatus status) {
        return ListQuestsResponse.newBuilder()
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode(questCode)
                        .setStatus(status)
                        .build())
                .build();
    }

    @Test
    @DisplayName("ListQuestsResponse nạp nhật ký — nhiệm vụ COMPLETED lĩnh được qua log dù không đang theo dõi")
    void logMakesACompletedQuestClaimableByCode() {
        tracker.onQuestsListed(listQuestsResponse("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED));

        assertThat(tracker.isTracking()).isFalse(); // KHÔNG đi qua onAccepted() — đúng ý "log độc lập"
        assertThat(tracker.isClaimable("kill_training_dummies")).isTrue();
        assertThat(tracker.questLog())
                .containsExactly(
                        new QuestTracker.LoggedQuest("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED));
    }

    /**
     * ĐÂY LÀ TEST TÁI HIỆN BUG THẬT: nhận nhiệm vụ (trong phiên chơi cũ) → server báo hoàn thành →
     * MẤT HẾT trạng thái cục bộ (tương đương relog — không có API nào "quên" ngoài dựng
     * {@link QuestTracker} mới, đúng những gì xảy ra khi {@code WorldScreen} bị huỷ và dựng lại) →
     * nhận {@code ListQuestsResponse} (opcode 611, thứ KHÔNG tồn tại trước ADR-035) → lĩnh thưởng
     * được. Trước ADR-035, bước cuối là bất khả thi: không có opcode nào hỏi lại được
     * {@code quest_code}, và cả {@code isClaimable()} lẫn {@code claimKey()} (không tham số) đều
     * đọc từ biến chỉ sống trong phiên chơi cũ — thứ vừa "mất" ở bước relog.
     */
    @Test
    @DisplayName("BUG THẬT: nhận nhiệm vụ → relog (mất tracker cũ) → ListQuestsResponse → vẫn lĩnh thưởng được")
    void survivesRelogViaListQuestsResponse() {
        // Phiên chơi CŨ: nhận nhiệm vụ, server báo hoàn thành.
        QuestTracker oldSessionTracker = new QuestTracker();
        oldSessionTracker.onAccepted("kill_training_dummies");
        oldSessionTracker.onStatusChanged("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED);
        assertThat(oldSessionTracker.isClaimable()).isTrue(); // xong TRƯỚC lúc relog

        // RELOG: một QuestTracker MỚI, không thừa hưởng gì từ cái cũ — questCode/status/pendingClaimKey
        // của phiên trước biến mất đúng như javadoc lớp mô tả.
        QuestTracker afterRelog = new QuestTracker();
        assertThat(afterRelog.isTracking()).isFalse();
        assertThat(afterRelog.isClaimable()).isFalse(); // "đang theo dõi" trống — bug gốc dừng lại ở đây

        // Server trả nhật ký (611) — nhiệm vụ hoàn thành TRƯỚC lúc relog vẫn còn trong đó.
        afterRelog.onQuestsListed(listQuestsResponse("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED));
        assertThat(afterRelog.isClaimable("kill_training_dummies")).isTrue();

        // Lĩnh thưởng qua đường MÃ (không qua "đang theo dõi", thứ vẫn trống) — sinh khoá, gửi lệnh,
        // server xác nhận.
        String claimKey = afterRelog.claimKey("kill_training_dummies");
        assertThat(claimKey).isNotBlank();
        afterRelog.onClaimSettled(true);

        assertThat(afterRelog.isClaimable("kill_training_dummies")).isFalse();
        assertThat(afterRelog.questLog())
                .containsExactly(
                        new QuestTracker.LoggedQuest("kill_training_dummies", QuestStatus.QUEST_STATUS_CLAIMED));
    }

    @Test
    @DisplayName("Khoá nhận thưởng theo mã: giữ nguyên khi retry cùng một mã, sinh mới cho mã khác")
    void claimKeyByCodeFollowsTheSameAdr025Rule() {
        tracker.onQuestsListed(ListQuestsResponse.newBuilder()
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("quest_a")
                        .setStatus(QuestStatus.QUEST_STATUS_COMPLETED)
                        .build())
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("quest_b")
                        .setStatus(QuestStatus.QUEST_STATUS_COMPLETED)
                        .build())
                .build());

        String firstForA = tracker.claimKey("quest_a");
        assertThat(tracker.claimKey("quest_a")).isEqualTo(firstForA); // retry cùng mã — giữ nguyên
        assertThat(tracker.isClaimPending("quest_a")).isTrue();
        assertThat(tracker.isClaimPending("quest_b")).isFalse();

        String forB = tracker.claimKey("quest_b");
        assertThat(forB).isNotEqualTo(firstForA); // mã KHÁC — khoá KHÁC, và giờ B mới là lệnh đang treo
        assertThat(tracker.isClaimPending("quest_a")).isFalse();
        assertThat(tracker.isClaimPending("quest_b")).isTrue();
    }

    @Test
    @DisplayName("onQuestsListed đồng bộ lại ô 'đang theo dõi' theo bản mới nhất của log")
    void listedQuestsResyncTheTrackedStatus() {
        tracker.onAccepted("kill_training_dummies");
        assertThat(tracker.status()).isEqualTo(QuestStatus.QUEST_STATUS_IN_PROGRESS);

        // Server nói nhiệm vụ ĐANG theo dõi thật ra đã COMPLETED (VD hoàn thành đúng lúc client vừa
        // mất kết nối) — nhật ký đầy đủ phải thắng bản cũ ở ô góc màn hình.
        tracker.onQuestsListed(listQuestsResponse("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED));

        assertThat(tracker.status()).isEqualTo(QuestStatus.QUEST_STATUS_COMPLETED);
        assertThat(tracker.isClaimable()).isTrue();
    }

    @Test
    @DisplayName("reset() xoá sạch CẢ ô đang theo dõi lẫn nhật ký đầy đủ")
    void resetClearsEverything() {
        tracker.onAccepted("kill_training_dummies");
        tracker.onStatusChanged("kill_training_dummies", QuestStatus.QUEST_STATUS_COMPLETED);
        tracker.onQuestsListed(listQuestsResponse("another_quest", QuestStatus.QUEST_STATUS_IN_PROGRESS));

        tracker.reset();

        assertThat(tracker.isTracking()).isFalse();
        assertThat(tracker.isClaimable()).isFalse();
        assertThat(tracker.questLog()).isEmpty();
        assertThat(tracker.status()).isEqualTo(QuestStatus.QUEST_STATUS_UNSPECIFIED);
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n — phủ hết MỖI GIÁ TRỊ của QuestStatus và ClaimQuestRewardOutcome, kể cả
    // UNSPECIFIED (khác quy ước MailControllerTest loại UNSPECIFIED ra — bài học thật ghi trong
    // javadoc QuestTracker: một vòng gộp trước đây thêm 6 outcome mà không thêm khoá i18n tương
    // ứng, người chơi chỉ đọc được "không rõ lỗi"). UNRECOGNIZED CỐ TÌNH bị loại: nó không phải giá
    // trị .proto định nghĩa mà là artefact codegen cho số hiệu enum lạ.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Mọi giá trị THẬT của QuestStatus đều có khoá i18n riêng")
    void everyQuestStatusHasAMessageKey() {
        for (QuestStatus status : QuestStatus.values()) {
            if (isRealEnumValue(status.name())) {
                assertThat(QuestTracker.statusMessageKey(status))
                        .as("QuestStatus.%s chưa có khoá i18n", status)
                        .hasValueSatisfying(key -> assertThat(key).startsWith("quest."));
            }
        }
    }

    @Test
    @DisplayName("Mọi giá trị THẬT của ClaimQuestRewardOutcome đều có khoá i18n riêng")
    void everyClaimQuestRewardOutcomeHasAMessageKey() {
        for (ClaimQuestRewardOutcome outcome : ClaimQuestRewardOutcome.values()) {
            if (isRealEnumValue(outcome.name())) {
                assertThat(QuestTracker.claimOutcomeMessageKey(outcome))
                        .as("ClaimQuestRewardOutcome.%s chưa có khoá i18n", outcome)
                        .hasValueSatisfying(key -> assertThat(key).startsWith("quest."));
            }
        }
    }

    @Test
    @DisplayName("UNRECOGNIZED (artefact codegen, không phải giá trị .proto) không có khoá — dịch thành 'không rõ'")
    void unrecognizedHasNoKey() {
        assertThat(QuestTracker.statusMessageKey(QuestStatus.UNRECOGNIZED)).isEmpty();
        assertThat(QuestTracker.claimOutcomeMessageKey(ClaimQuestRewardOutcome.UNRECOGNIZED))
                .isEmpty();
    }

    private static boolean isRealEnumValue(String name) {
        return !"UNRECOGNIZED".equals(name);
    }
}
