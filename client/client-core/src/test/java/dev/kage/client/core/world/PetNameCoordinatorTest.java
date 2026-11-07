package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.RenamePetOutcome;
import dev.kage.protocol.RenamePetResponse;

/** ADR-193 — mọi kết quả đặt tên linh thú đều có câu dịch. */
final class PetNameCoordinatorTest {

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
        for (RenamePetOutcome outcome : RenamePetOutcome.values()) {
            if (outcome == RenamePetOutcome.UNRECOGNIZED) {
                continue;
            }
            RenamePetResponse response = RenamePetResponse.newBuilder()
                    .setOutcome(outcome)
                    .setName("Lan")
                    .build();
            assertThat(PetNameCoordinator.describe(KageI18n.load(), response)).doesNotStartWith("pet_name.");
        }
    }
}
