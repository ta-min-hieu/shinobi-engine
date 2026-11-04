package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ClaimAllMailResponse;

/** ADR-167 — dòng tóm tắt nhận hết thư; túi đầy thì nói thêm còn thư chưa lĩnh. */
final class MailBulkCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    private final MailBulkCoordinator coordinator = new MailBulkCoordinator(null, null);

    @Test
    void shouldSummariseWhatWasClaimed() {
        String text = coordinator.describe(ClaimAllMailResponse.newBuilder()
                .setClaimed(7)
                .setGold(750)
                .setItems(30)
                .setDeleted(8)
                .build());

        assertThat(text).contains("7").contains("750").contains("30").contains("8");
    }

    @Test
    void fullBagShouldBeMentioned() {
        String full = coordinator.describe(
                ClaimAllMailResponse.newBuilder().setClaimed(4).setBagFull(true).build());
        String notFull = coordinator.describe(
                ClaimAllMailResponse.newBuilder().setClaimed(4).build());

        assertThat(full.length()).isGreaterThan(notFull.length());
    }
}
