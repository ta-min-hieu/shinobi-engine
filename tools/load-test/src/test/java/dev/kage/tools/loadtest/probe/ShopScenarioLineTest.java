package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Cú pháp {@code code[:soLuong]} của {@code --sellItems}/{@code --buyItems} — thuần logic, không cần server. */
final class ShopScenarioLineTest {

    @Test
    void shouldUseDefaultQuantity_whenNoneGiven() {
        assertThat(ShopScenario.Line.parse("iron_scrap", ShopScenario.ALL))
                .isEqualTo(new ShopScenario.Line("iron_scrap", ShopScenario.ALL));
        assertThat(ShopScenario.Line.parse("novice_vest", 1)).isEqualTo(new ShopScenario.Line("novice_vest", 1));
    }

    @Test
    void shouldReadExplicitQuantity() {
        assertThat(ShopScenario.Line.parse("kunai:5", ShopScenario.ALL)).isEqualTo(new ShopScenario.Line("kunai", 5));
    }

    @Test
    void shouldRefuseNonPositiveOrGarbledQuantity() {
        assertThatThrownBy(() -> ShopScenario.Line.parse("kunai:0", 1)).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> ShopScenario.Line.parse("kunai:x", 1)).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldCarryShopListsThroughProbeConfig() {
        ProbeConfig config = ProbeConfig.parse(
                new String[] {"--sellItems=iron_scrap,kunai:3", "--buyItems=wooden_sword,novice_vest"});

        assertThat(config.sellItems()).containsExactly("iron_scrap", "kunai:3");
        assertThat(config.buyItems()).containsExactly("wooden_sword", "novice_vest");
        assertThat(ProbeConfig.parse(new String[0]).sellItems()).isEmpty();
    }
}
