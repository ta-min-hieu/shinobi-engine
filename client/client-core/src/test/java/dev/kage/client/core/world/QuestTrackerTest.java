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

        // Từ 2026-09-05 nhật ký CÓ nhận lại ô "đang theo dõi" khi nó đang trống (xem
        // QuestTracker#adoptFromLog) — đó là cách HUD góc màn hình sống sót qua relog. Ý "log độc
        // lập" vẫn nguyên: lĩnh thưởng theo MÃ không hề đi qua hai trường đó, hai dòng dưới chứng minh.
        assertThat(tracker.isTracking()).isTrue();
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

    /**
     * LỖI THẬT bắt được khi PLAYTEST SỐNG 2026-09-05: đăng nhập giữa chừng một nhiệm vụ thì HUD
     * góc màn hình TRỐNG TRƠN — không còn dấu vết nào cho thấy mình đang làm gì, cho tới khi tình
     * cờ bấm J. Dữ liệu không thiếu (nhật ký 611 về đủ); chỉ là không ai đọc nó sang ô "đang theo dõi".
     */
    @Test
    @DisplayName("LỖI THẬT: relog giữa chừng nhiệm vụ — nhật ký phải đưa lại nó lên HUD góc màn hình")
    void adoptsAnInProgressQuestFromTheLogAfterRelog() {
        QuestTracker afterRelog = new QuestTracker();
        assertThat(afterRelog.isTracking()).isFalse(); // phiên mới: hai trường trong phiên trống

        afterRelog.onQuestsListed(listQuestsResponse("night_watch_bandit", QuestStatus.QUEST_STATUS_IN_PROGRESS));

        assertThat(afterRelog.isTracking()).isTrue();
        assertThat(afterRelog.questCode()).isEqualTo("night_watch_bandit");
        assertThat(afterRelog.status()).isEqualTo(QuestStatus.QUEST_STATUS_IN_PROGRESS);
        assertThat(afterRelog.isClaimable()).isFalse(); // chưa xong thì chưa có gì để lĩnh
    }

    /** Nhiệm vụ XONG đang chờ bấm Lĩnh thưởng thắng nhiệm vụ đang làm: nó cần người chơi LÀM một việc. */
    @Test
    @DisplayName("Nhận lại từ nhật ký ưu tiên nhiệm vụ HOÀN THÀNH hơn nhiệm vụ đang làm")
    void adoptsTheCompletedQuestFirst() {
        QuestTracker afterRelog = new QuestTracker();

        afterRelog.onQuestsListed(ListQuestsResponse.newBuilder()
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("kill_training_dummies")
                        .setStatus(QuestStatus.QUEST_STATUS_IN_PROGRESS)
                        .build())
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("night_watch_bandit")
                        .setStatus(QuestStatus.QUEST_STATUS_COMPLETED)
                        .build())
                .build());

        assertThat(afterRelog.questCode()).isEqualTo("night_watch_bandit"); // dù đứng SAU trong danh sách
        assertThat(afterRelog.isClaimable()).isTrue();
    }

    /** Nhiệm vụ đã lĩnh xong không đáng chiếm chỗ HUD — không còn việc gì để làm với nó. */
    @Test
    @DisplayName("Nhật ký toàn nhiệm vụ đã lĩnh thưởng thì HUD góc màn hình vẫn trống")
    void doesNotAdoptAlreadyClaimedQuests() {
        QuestTracker afterRelog = new QuestTracker();

        afterRelog.onQuestsListed(listQuestsResponse("kill_training_dummies", QuestStatus.QUEST_STATUS_CLAIMED));

        assertThat(afterRelog.isTracking()).isFalse();
    }

    /**
     * Người chơi vắt hết nội dung đang cầm thì phải được BÁO, không đâm vào tường vô hình.
     *
     * <p>Trước bản vá này {@code QuestCoordinator.listNotice} chỉ nói khi nhật ký RỖNG, nên người
     * cày xong mọi nhiệm vụ (tổng &gt; 0) thấy một danh sách toàn "đã nhận thưởng" và không một câu
     * nào nói họ phải làm gì tiếp.
     */
    @Test
    @DisplayName("Mọi dòng đều đã lĩnh thưởng ⇒ everyQuestClaimed() đúng")
    void reportsEveryQuestClaimedWhenTheWholeLogIsClaimed() {
        QuestTracker afterRelog = new QuestTracker();

        afterRelog.onQuestsListed(ListQuestsResponse.newBuilder()
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("kill_training_dummies")
                        .setStatus(QuestStatus.QUEST_STATUS_CLAIMED)
                        .build())
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("night_watch_bandit")
                        .setStatus(QuestStatus.QUEST_STATUS_CLAIMED)
                        .build())
                .build());

        assertThat(afterRelog.everyQuestClaimed()).isTrue();
    }

    /** Còn MỘT dòng chưa lĩnh là chưa hết việc — câu "hết nội dung" nói ra lúc này là nói dối. */
    @Test
    @DisplayName("Còn một dòng chưa lĩnh thưởng ⇒ everyQuestClaimed() sai")
    void doesNotReportEveryQuestClaimedWhileOneIsStillOpen() {
        QuestTracker afterRelog = new QuestTracker();

        afterRelog.onQuestsListed(ListQuestsResponse.newBuilder()
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("kill_training_dummies")
                        .setStatus(QuestStatus.QUEST_STATUS_CLAIMED)
                        .build())
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("night_watch_bandit")
                        .setStatus(QuestStatus.QUEST_STATUS_IN_PROGRESS)
                        .build())
                .build());

        assertThat(afterRelog.everyQuestClaimed()).isFalse();
    }

    /**
     * Nhật ký RỖNG là "chưa nhận nhiệm vụ nào", KHÔNG phải "đã hết nội dung" — hai câu khác hẳn
     * nhau, và người mới tạo nhân vật rơi vào câu thứ nhất. Đây là chỗ dễ nói dối nhất của tính
     * năng này nên nó có ô riêng.
     */
    @Test
    @DisplayName("Nhật ký rỗng ⇒ everyQuestClaimed() sai, vì đó là 'chưa nhận', không phải 'đã hết'")
    void doesNotMistakeAnEmptyLogForFinishedContent() {
        assertThat(new QuestTracker().everyQuestClaimed()).isFalse();
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

    @Test
    @DisplayName("Bỏ nhiệm vụ đang theo dõi (ADR-054) ⇒ góc màn hình nhận nhiệm vụ khác, nhật ký mất dòng đó")
    void abandoningTheTrackedQuestAdoptsAnotherOne() {
        tracker.onQuestsListed(ListQuestsResponse.newBuilder()
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("kill_training_dummies")
                        .setStatus(QuestStatus.QUEST_STATUS_IN_PROGRESS))
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("night_watch_bandit")
                        .setStatus(QuestStatus.QUEST_STATUS_IN_PROGRESS))
                .build());
        String tracked = tracker.questCode();

        tracker.onAbandoned(tracked);

        assertThat(tracker.isTracking()).isTrue();
        assertThat(tracker.questCode()).isNotEqualTo(tracked);
        assertThat(tracker.questLog())
                .extracting(QuestTracker.LoggedQuest::questCode)
                .doesNotContain(tracked);
    }

    @Test
    @DisplayName("Bỏ nhiệm vụ cuối cùng ⇒ góc màn hình trống, không đứng mãi với nhiệm vụ đã bỏ")
    void abandoningTheOnlyQuestClearsTheTracker() {
        tracker.onAccepted("kill_training_dummies");

        tracker.onAbandoned("kill_training_dummies");

        assertThat(tracker.isTracking()).isFalse();
    }
}
