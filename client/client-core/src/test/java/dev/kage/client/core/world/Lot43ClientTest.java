package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.hud.MailPanel;
import dev.kage.protocol.MailView;

/** Lô 43 client — phiên (ADR-271/272), biệt danh (273), tìm thư (275), bạn yêu thích (276), thư nháp (277). */
class Lot43ClientTest {

    private static final long MINUTE = 60_000L;

    @Test
    void sessionTrackerCounts() {
        SessionTracker tracker = new SessionTracker(0L);
        tracker.onExperience(1_000L, 100L, 500L, false); // gói vào world — chỉ ghi mốc
        tracker.onExperience(1_250L, 350L, 500L, false);
        tracker.onExperience(1_400L, 0L, 800L, true);
        tracker.onGold(300L);
        tracker.onGold(-120L);
        tracker.onKill();
        tracker.onItemGranted(3);
        tracker.onItemGranted(-1);

        assertThat(tracker.expGained()).isEqualTo(400L);
        assertThat(tracker.levelsGained()).isEqualTo(1);
        assertThat(tracker.goldGained()).isEqualTo(300L);
        assertThat(tracker.goldSpent()).isEqualTo(120L);
        assertThat(tracker.kills()).isEqualTo(1);
        assertThat(tracker.itemsLooted()).isEqualTo(3);
    }

    @Test
    void expRateAndEta() {
        SessionTracker tracker = new SessionTracker(0L);
        assertThat(tracker.expPerHour(30_000L)).isEmpty();
        tracker.onExperience(0L, 0L, 1_000L, false);
        tracker.onExperience(600L, 600L, 1_000L, false);
        // 600 EXP trong 30 phút ⇒ 1200 EXP/giờ; còn 400 EXP ⇒ 20 phút
        assertThat(tracker.expPerHour(30 * MINUTE)).hasValue(1_200L);
        assertThat(tracker.minutesToNextLevel(30 * MINUTE)).hasValue(20L);

        SessionTracker maxed = new SessionTracker(0L);
        maxed.onExperience(0L, 0L, 0L, false);
        maxed.onExperience(10L, 0L, 0L, false);
        assertThat(maxed.minutesToNextLevel(30 * MINUTE)).isEmpty();
    }

    @Test
    void aliasExpandsOnce() {
        Map<String, String> table = Map.of("a", "/khobang", "b", "/a");
        assertThat(CommandAliasCoordinator.expand("/a", table)).isEqualTo("/khobang");
        assertThat(CommandAliasCoordinator.expand("/A rút 5", table)).isEqualTo("/khobang rút 5");
        assertThat(CommandAliasCoordinator.expand("/b", table)).isEqualTo("/a"); // không lặp
        assertThat(CommandAliasCoordinator.expand("xin chào", table)).isEqualTo("xin chào");
        assertThat(CommandAliasCoordinator.validTarget("/khobang")).isTrue();
        assertThat(CommandAliasCoordinator.validTarget("/alias x /y")).isFalse();
        assertThat(CommandAliasCoordinator.validTarget("khobang")).isFalse();
        assertThat(CommandAliasCoordinator.parse("a=/khobang\nbad\nZZ=/x\nb=/so\n"))
                .containsOnlyKeys("a", "b");
    }

    @Test
    void mailSearchMatches() {
        MailView fromPlayer = MailView.newBuilder()
                .setMailId(1L)
                .setHasSender(true)
                .setSubject("Trả nợ")
                .setBody("Gửi bạn 500 linh thạch")
                .build();
        MailView fromSystem = MailView.newBuilder()
                .setMailId(2L)
                .setSubject("Điểm danh")
                .setBody("Quà ngày 3")
                .build();
        List<MailView> box = List.of(fromPlayer, fromSystem);
        assertThat(MailSearchCoordinator.matches(box, "tra no")).containsExactly(fromPlayer);
        assertThat(MailSearchCoordinator.matches(box, "LINH THẠCH")).containsExactly(fromPlayer);
        assertThat(MailSearchCoordinator.matches(box, "hethong")).containsExactly(fromSystem);
        assertThat(MailSearchCoordinator.matches(box, "không có")).isEmpty();
        assertThat(MailSearchCoordinator.snippet("x".repeat(50))).hasSize(MailSearchCoordinator.SNIPPET + 1);
    }

    @Test
    void favoritesParseFoldedNames() {
        assertThat(FavoriteFriendsCoordinator.parse("Đạo Hữu\n\nKage\n")).containsExactly("dao huu", "kage");
    }

    @Test
    void mailDraftBlank() {
        assertThat(MailDraftStore.isBlank(new MailPanel.Composed(" ", "", "", "")))
                .isTrue();
        assertThat(MailDraftStore.isBlank(new MailPanel.Composed("", "", "dở", "")))
                .isFalse();
    }
}
