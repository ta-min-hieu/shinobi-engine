package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.SetAwayMessageOutcome;
import dev.kage.protocol.SetAwayMessageResponse;

/** ADR-178 — câu báo bật/tắt vắng mặt. */
final class AwayCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void describeShouldCoverOnOffAndRefusals() {
        SetAwayMessageResponse on = SetAwayMessageResponse.newBuilder()
                .setOutcome(SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_OK)
                .setAway(true)
                .setMessage("đi ăn cơm")
                .build();
        assertThat(AwayCoordinator.describe(KageI18n.load(), on)).contains("đi ăn cơm");
        assertThat(AwayCoordinator.describe(
                        KageI18n.load(),
                        on.toBuilder().setAway(false).setMessage("").build()))
                .doesNotStartWith("away.");
        assertThat(AwayCoordinator.describe(
                        KageI18n.load(),
                        on.toBuilder()
                                .setOutcome(SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_TOO_LONG)
                                .build()))
                .doesNotStartWith("away.");
    }
}
