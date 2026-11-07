package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetResetTimersResponse;

/** ADR-200 — thời gian còn lại tính từ mốc server, hai đơn vị lớn nhất. */
final class ResetTimersCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void describesBothTimers() {
        GetResetTimersResponse response = GetResetTimersResponse.newBuilder()
                .setServerNowEpochSeconds(1_000L)
                .setNextDailyEpochSeconds(1_000L + 3 * 3600 + 12 * 60)
                .setNextWeeklyEpochSeconds(1_000L + 2 * 86_400 + 5 * 3600)
                .build();

        String line = ResetTimersCoordinator.describe(KageI18n.load(), response);

        assertThat(line)
                .doesNotContain("reset_timers.")
                .contains("3")
                .contains("12")
                .contains("2")
                .contains("5");
        assertThat(ResetTimersCoordinator.duration(KageI18n.load(), 30)).contains("1");
    }
}
