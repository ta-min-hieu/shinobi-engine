package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.SetMarketWatchOutcome;

/** ADR-196 — mọi kết quả theo dõi phường thị và các dòng chung đều có câu dịch. */
final class MarketWatchCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void everyKeyIsTranslated() {
        for (SetMarketWatchOutcome outcome : SetMarketWatchOutcome.values()) {
            if (outcome == SetMarketWatchOutcome.UNRECOGNIZED) {
                continue;
            }
            String key = MarketWatchCoordinator.outcomeKey(outcome);
            assertThat(KageI18n.textOrKey(KageI18n.load(), key)).as(key).isNotEqualTo(key);
        }
        for (String key : new String[] {
            "market_watch.usage", "market_watch.empty", "market_watch.header", "market_watch.row", "market_watch.alert"
        }) {
            assertThat(KageI18n.textOrKey(KageI18n.load(), key)).as(key).isNotEqualTo(key);
        }
    }
}
