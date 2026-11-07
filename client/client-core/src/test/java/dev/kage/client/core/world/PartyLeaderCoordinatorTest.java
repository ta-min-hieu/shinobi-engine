package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.TransferPartyLeaderOutcome;
import dev.kage.protocol.TransferPartyLeaderResponse;

/** ADR-191 — mọi kết quả nhường trưởng nhóm đều có câu dịch. */
final class PartyLeaderCoordinatorTest {

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
        for (TransferPartyLeaderOutcome outcome : TransferPartyLeaderOutcome.values()) {
            if (outcome == TransferPartyLeaderOutcome.UNRECOGNIZED) {
                continue;
            }
            TransferPartyLeaderResponse response = TransferPartyLeaderResponse.newBuilder()
                    .setOutcome(outcome)
                    .setTargetName("Lan")
                    .build();
            assertThat(PartyLeaderCoordinator.describe(KageI18n.load(), response))
                    .doesNotStartWith("party_leader.");
        }
    }
}
