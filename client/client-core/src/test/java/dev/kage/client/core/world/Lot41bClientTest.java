package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Lô 41 thuần client — hẹn giờ (ADR-259), giờ server (ADR-260), sổ ghi chép (ADR-261). */
class Lot41bClientTest {

    @Test
    void parsesReminderMinutesAndText() {
        assertThat(ReminderTimerCoordinator.parse("15 đi điểm danh bang"))
                .contains(new ReminderTimerCoordinator.Parsed(15, "đi điểm danh bang"));
        assertThat(ReminderTimerCoordinator.parse("0 x")).isEmpty();
        assertThat(ReminderTimerCoordinator.parse("601 x")).isEmpty();
        assertThat(ReminderTimerCoordinator.parse("15")).isEmpty();
        assertThat(ReminderTimerCoordinator.parse("abc x")).isEmpty();
    }

    @Test
    void formatsServerTimeInZone() {
        assertThat(ServerClockCoordinator.format(0L, ZoneOffset.ofHours(7))).isEqualTo("07:00:00 01/01");
        assertThat(ServerClockCoordinator.SERVER_ZONE.getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void notebookSkipsBlankAndCaps() {
        assertThat(NotebookCoordinator.parse("a\n\n  b  \n")).containsExactly("a", "b");
        assertThat(NotebookCoordinator.parse("x\n".repeat(30))).hasSize(NotebookCoordinator.MAX_NOTES);
    }
}
