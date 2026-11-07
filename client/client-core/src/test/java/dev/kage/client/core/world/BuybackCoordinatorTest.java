package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuybackOutcome;

/** ADR-202 — mọi kết quả mua lại và các dòng chung đều có câu dịch. */
final class BuybackCoordinatorTest {

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
        for (BuybackOutcome outcome : BuybackOutcome.values()) {
            if (outcome == BuybackOutcome.UNRECOGNIZED) {
                continue;
            }
            String key = BuybackCoordinator.outcomeKey(outcome);
            assertThat(KageI18n.textOrKey(KageI18n.load(), key)).as(key).isNotEqualTo(key);
        }
        for (String key : new String[] {"buyback.usage", "buyback.empty", "buyback.row"}) {
            assertThat(KageI18n.textOrKey(KageI18n.load(), key)).as(key).isNotEqualTo(key);
        }
    }
}
