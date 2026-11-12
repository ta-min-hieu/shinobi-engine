package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.checkin.domain.WeeklyTaskPort;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 44 — cảnh giới theo cấp (ADR-281), đếm việc tuần chưa lĩnh (ADR-282), dòng báo tự vào nhóm (ADR-286). */
class Lot44ServerTest {

    @Test
    void mapsLevelsToSixRealms() {
        assertThat(Lot44Commands.realmOf(1)).isZero();
        assertThat(Lot44Commands.realmOf(18)).isZero();
        assertThat(Lot44Commands.realmOf(19)).isEqualTo(1);
        assertThat(Lot44Commands.realmOf(90)).isEqualTo(4);
        assertThat(Lot44Commands.realmOf(91)).isEqualTo(5);
        assertThat(Lot44Commands.realmOf(100)).isEqualTo(5);
        assertThat(Lot44Commands.realmOf(0)).isZero();
    }

    @Test
    void countsUnclaimedWeeklyTasks() {
        List<WeeklyTaskPort.Task> tasks = List.of(
                new WeeklyTaskPort.Task("a", "k", 5, 5, false, 0L, 0L, 0),
                new WeeklyTaskPort.Task("b", "k", 5, 2, false, 0L, 0L, 0),
                new WeeklyTaskPort.Task("c", "k", 5, 5, true, 0L, 0L, 0));
        assertThat(WeeklyReminder.missing(tasks)).isEqualTo(new WeeklyReminder.Missing(2, 1));
    }

    @Test
    void noticesAreBilingual() throws InvalidProtocolBufferException {
        assertThat(ChatMessageEvent.parseFrom(WeeklyReminder.notice(new WeeklyReminder.Missing(2, 1)))
                        .getBody())
                .contains("còn 2 nhiệm vụ tuần")
                .contains("2 weekly tasks");
        assertThat(ChatMessageEvent.parseFrom(Lot44Commands.autoAcceptNotice("BaoDo01"))
                        .getBody())
                .contains("BaoDo01");
    }
}
