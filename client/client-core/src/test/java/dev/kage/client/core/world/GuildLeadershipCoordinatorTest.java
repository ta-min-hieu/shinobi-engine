package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.TransferGuildLeaderOutcome;
import dev.kage.protocol.TransferGuildLeaderResponse;

/** ADR-183 — mọi kết quả nhường chức đều có câu dịch. */
final class GuildLeadershipCoordinatorTest {

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
        for (TransferGuildLeaderOutcome outcome : TransferGuildLeaderOutcome.values()) {
            if (outcome == TransferGuildLeaderOutcome.UNRECOGNIZED) {
                continue;
            }
            TransferGuildLeaderResponse response = TransferGuildLeaderResponse.newBuilder()
                    .setOutcome(outcome)
                    .setTargetName("Lan")
                    .build();
            assertThat(GuildLeadershipCoordinator.describe(KageI18n.load(), response))
                    .doesNotStartWith("guild_leader.");
        }
    }
}
