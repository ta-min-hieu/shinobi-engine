package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.SetGuildViceLeaderOutcome;
import dev.kage.protocol.SetGuildViceLeaderResponse;

/** ADR-184 — mọi kết quả bổ nhiệm / bãi nhiệm phó bang chủ đều có câu dịch. */
final class GuildViceCoordinatorTest {

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
        for (SetGuildViceLeaderOutcome outcome : SetGuildViceLeaderOutcome.values()) {
            if (outcome == SetGuildViceLeaderOutcome.UNRECOGNIZED) {
                continue;
            }
            for (boolean appoint : new boolean[] {true, false}) {
                SetGuildViceLeaderResponse response = SetGuildViceLeaderResponse.newBuilder()
                        .setOutcome(outcome)
                        .setTargetName("Lan")
                        .setAppoint(appoint)
                        .build();
                assertThat(GuildViceCoordinator.describe(KageI18n.load(), response))
                        .doesNotStartWith("guild_vice.");
            }
        }
    }
}
