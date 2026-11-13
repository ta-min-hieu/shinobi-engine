package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.account.api.LoginHistoryFacade;
import dev.kage.account.domain.LoginHistoryPort;
import dev.kage.character.api.LoginStreakFacade;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 50 — chuỗi đăng nhập (ADR-327), mạng lạ (ADR-328), báo bị soi (ADR-330). */
class Lot50ServerTest {

    @Test
    void streakRewardsOnMilestones() {
        assertThat(LoginStreakFacade.rewardFor(1)).isZero();
        assertThat(LoginStreakFacade.rewardFor(3)).isEqualTo(LoginStreakFacade.FIRST_REWARD);
        assertThat(LoginStreakFacade.rewardFor(7)).isEqualTo(LoginStreakFacade.WEEK_REWARD);
        assertThat(LoginStreakFacade.rewardFor(14)).isEqualTo(LoginStreakFacade.WEEK_REWARD);
        assertThat(LoginStreakFacade.nextMilestone(0)).isEqualTo(3);
        assertThat(LoginStreakFacade.nextMilestone(3)).isEqualTo(7);
        assertThat(LoginStreakFacade.nextMilestone(7)).isEqualTo(14);
    }

    @Test
    void streakKeyIsPerDay() {
        LocalDate day = LocalDate.of(2026, 11, 13);
        assertThat(Lot50Commands.streakKey(5, day)).isEqualTo(Lot50Commands.streakKey(5, day));
        assertThat(Lot50Commands.streakKey(5, day)).isNotEqualTo(Lot50Commands.streakKey(5, day.plusDays(1)));
    }

    @Test
    void streakNoticeMentionsGiftOnlyOnMilestone() throws InvalidProtocolBufferException {
        assertThat(ChatMessageEvent.parseFrom(Lot50Commands.streakNotice(2, 0)).getBody())
                .contains("2 ngày")
                .doesNotContain("linh thạch");
        assertThat(ChatMessageEvent.parseFrom(Lot50Commands.streakNotice(7, 1_500))
                        .getBody())
                .contains("1500 linh thạch");
    }

    @Test
    void newNetworkOnlyWhenUnseenBefore() {
        Instant at = Instant.EPOCH;
        LoginHistoryPort.Entry home = new LoginHistoryPort.Entry("203.113.x.x", at);
        LoginHistoryPort.Entry away = new LoginHistoryPort.Entry("118.70.x.x", at);
        LoginHistoryPort.Entry unknown = new LoginHistoryPort.Entry("?", at);
        assertThat(LoginHistoryFacade.isNewNetwork(List.of(home))).isFalse();
        assertThat(LoginHistoryFacade.isNewNetwork(List.of(home, home))).isFalse();
        assertThat(LoginHistoryFacade.isNewNetwork(List.of(away, home))).isTrue();
        assertThat(LoginHistoryFacade.isNewNetwork(List.of(away, home, away))).isFalse();
        assertThat(LoginHistoryFacade.isNewNetwork(List.of(unknown, home))).isFalse();
    }

    @Test
    void inspectedNoticeNamesViewer() throws InvalidProtocolBufferException {
        assertThat(ChatMessageEvent.parseFrom(Lot50Commands.inspectedNotice("BaoDo01"))
                        .getBody())
                .startsWith("BaoDo01 vừa xem trang bị của bạn.");
        assertThat(ChatMessageEvent.parseFrom(Lot50Commands.newNetworkNotice()).getBody())
                .contains("/lichsudangnhap");
    }
}
