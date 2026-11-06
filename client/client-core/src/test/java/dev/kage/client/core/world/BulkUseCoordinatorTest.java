package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.UseItemsResponse;

/** ADR-176 — đối số {@code /dungnhieu} và câu báo kết quả. */
final class BulkUseCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void parseCountShouldDefaultAndRejectNonsense() {
        assertThat(BulkUseCoordinator.parseCount("")).contains(BulkUseCoordinator.DEFAULT_COUNT);
        assertThat(BulkUseCoordinator.parseCount(" 7 ")).contains(7);
        assertThat(BulkUseCoordinator.parseCount("0")).isEmpty();
        assertThat(BulkUseCoordinator.parseCount("-3")).isEmpty();
        assertThat(BulkUseCoordinator.parseCount("abc")).isEmpty();
    }

    @Test
    void describeShouldReportCountAndStopReason() {
        UseItemsResponse full =
                UseItemsResponse.newBuilder().setUsed(5).setRemainingQuantity(2).build();
        assertThat(BulkUseCoordinator.describe(KageI18n.load(), full))
                .contains("5")
                .contains("2");
        UseItemsResponse stopped =
                full.toBuilder().setStopErrorKey("error.exp_buff_at_max").build();
        assertThat(BulkUseCoordinator.describe(KageI18n.load(), stopped))
                .contains("5")
                .doesNotContain("error.");
        UseItemsResponse refused = UseItemsResponse.newBuilder()
                .setStopErrorKey("error.item_not_bulk_usable")
                .build();
        assertThat(BulkUseCoordinator.describe(KageI18n.load(), refused)).doesNotStartWith("error.");
    }
}
