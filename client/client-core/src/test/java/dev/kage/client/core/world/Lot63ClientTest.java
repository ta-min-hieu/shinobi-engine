package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetOnlineWeekResponse;
import dev.kage.protocol.OnlineDay;

/** Lô 63 phía client — online 7 ngày (ADR-432), màu tên mình (ADR-436), nhắc việc trên HUD (ADR-437). */
class Lot63ClientTest {

    @Test
    void describesOnlineWeekWithTotal() {
        GetOnlineWeekResponse response = GetOnlineWeekResponse.newBuilder()
                .addDays(OnlineDay.newBuilder()
                        .setDayEpochDays(LocalDate.parse("2033-03-02").toEpochDay())
                        .setSeconds(120))
                .addDays(OnlineDay.newBuilder()
                        .setDayEpochDays(LocalDate.parse("2033-03-03").toEpochDay())
                        .setSeconds(3600))
                .build();
        String line = RankingMoreCoordinator.describeWeek(KageI18n.load(), response);
        assertThat(line).contains("62").contains("02/03 2'").contains("03/03 60'");
    }

    @Test
    void aliasesOwnNameColour() {
        assertThat(HudPolishCoordinator.colorAlias("/mautenminh vang")).isEqualTo("/maukenh minh vang");
        assertThat(HudPolishCoordinator.colorAlias("/MYCOLOR")).isEqualTo("/maukenh minh ");
        assertThat(HudPolishCoordinator.colorAlias("/maukenh the_gioi do")).isEqualTo("/maukenh the_gioi do");
    }

    @Test
    void secondsToNearestReminder() {
        assertThat(ReminderTimerCoordinator.secondsToNext(List.of(), 0L)).isEqualTo(-1L);
        assertThat(ReminderTimerCoordinator.secondsToNext(List.of(90_000L, 30_000L), 0L))
                .isEqualTo(30L);
        assertThat(ReminderTimerCoordinator.secondsToNext(List.of(5_000L), 10_000L))
                .isZero();
    }
}
