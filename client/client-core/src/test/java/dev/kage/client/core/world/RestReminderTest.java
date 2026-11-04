package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** ADR-174 — nhắc lần đầu sau 2 giờ, rồi mỗi giờ; tắt công tắc thì im lặng nhưng vẫn giữ nhịp. */
final class RestReminderTest {

    private static final long START = 1_000L;
    private static final long HOUR = RestReminder.HOUR_NANOS;

    @Test
    void remindsAfterTwoHoursThenEveryHour() {
        List<String> notices = new ArrayList<>();
        RestReminder reminder = new RestReminder(hours -> "h" + hours, notices::add);

        reminder.update(START);
        reminder.update(START + 2 * HOUR - 1);
        assertThat(notices).isEmpty();
        reminder.update(START + 2 * HOUR);
        reminder.update(START + 2 * HOUR + 1);
        assertThat(notices).containsExactly("h2");
        reminder.update(START + 3 * HOUR);
        assertThat(notices).containsExactly("h2", "h3");
    }

    @Test
    void switchedOffStaysSilentAndResumesOnSchedule() {
        List<String> notices = new ArrayList<>();
        boolean[] on = {false};
        RestReminder reminder = new RestReminder(hours -> "h" + hours, notices::add);
        reminder.useSwitch(() -> on[0]);

        reminder.update(START);
        reminder.update(START + 2 * HOUR);
        assertThat(notices).isEmpty();
        on[0] = true;
        reminder.update(START + 2 * HOUR + 1);
        assertThat(notices).isEmpty();
        reminder.update(START + 3 * HOUR);
        assertThat(notices).containsExactly("h3");
    }
}
