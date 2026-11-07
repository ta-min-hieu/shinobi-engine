package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.TradeHistoryEntry;
import dev.kage.protocol.TradeHistoryItem;

/** ADR-189 — một dòng lịch sử giao dịch: với ai, đưa gì, nhận gì. */
final class TradeHistoryCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void rowListsBothSides() {
        TradeHistoryEntry entry = TradeHistoryEntry.newBuilder()
                .setCounterpartName("Lan")
                .setGoldGiven(500)
                .addReceived(TradeHistoryItem.newBuilder()
                        .setItemTemplateId(10)
                        .setQuantity(1)
                        .setEnhanceLevel(3))
                .setAtEpochSeconds(0)
                .build();
        String row = TradeHistoryCoordinator.describe(KageI18n.load(), entry, id -> "Trường kiếm", ZoneOffset.UTC);
        assertThat(row).contains("01/01 00:00").contains("Lan").contains("500").contains("Trường kiếm +3");
        String nothing = TradeHistoryCoordinator.describe(
                KageI18n.load(), TradeHistoryEntry.getDefaultInstance(), id -> "x", ZoneOffset.UTC);
        assertThat(nothing).doesNotContain("trade_history.");
    }
}
