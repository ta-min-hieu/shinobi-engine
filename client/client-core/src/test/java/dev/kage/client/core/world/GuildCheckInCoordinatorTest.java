package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildCheckInOutcome;
import dev.kage.protocol.GuildCheckInResponse;

/** ADR-186 — câu báo điểm danh bang. */
final class GuildCheckInCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void everyOutcomeIsTranslated() {
        for (GuildCheckInOutcome outcome : GuildCheckInOutcome.values()) {
            if (outcome == GuildCheckInOutcome.UNRECOGNIZED) {
                continue;
            }
            GuildCheckInResponse response = GuildCheckInResponse.newBuilder()
                    .setOutcome(outcome)
                    .setContribution(100)
                    .setGuildExp(100)
                    .setTodayCount(3)
                    .build();
            assertThat(GuildCheckInCoordinator.describe(KageI18n.load(), response))
                    .doesNotStartWith("guild_checkin.");
        }
    }
}
