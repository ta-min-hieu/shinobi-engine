package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.MarketHistoryEntry;

/** ADR-177 — một dòng lịch sử phường thị: bán / mua, số vàng, người kia. */
final class MarketHistoryCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void soldAndBoughtRowsCarryGoldAndCounterpart() {
        MarketHistoryEntry sold = MarketHistoryEntry.newBuilder()
                .setSold(true)
                .setItemTemplateId(7)
                .setQuantity(3)
                .setPrice(100)
                .setGoldDelta(95)
                .setCounterpartName("Lan")
                .setAtEpochSeconds(0)
                .build();
        String soldRow = MarketHistoryCoordinator.describe(KageI18n.load(), sold, id -> "Kiếm", ZoneOffset.UTC);
        assertThat(soldRow)
                .contains("01/01 00:00")
                .contains("Kiếm x3")
                .contains("+95")
                .contains("Lan");

        MarketHistoryEntry bought = sold.toBuilder()
                .setSold(false)
                .setGoldDelta(-100)
                .setCounterpartName("")
                .build();
        String boughtRow = MarketHistoryCoordinator.describe(KageI18n.load(), bought, id -> "Kiếm", ZoneOffset.UTC);
        assertThat(boughtRow).contains("-100").doesNotContain("+").doesNotStartWith("market_history.");
    }
}
