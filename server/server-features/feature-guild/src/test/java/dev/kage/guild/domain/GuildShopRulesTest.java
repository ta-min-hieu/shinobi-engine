package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-080 — điểm tiêu được = góp − đã tiêu; cấp bang trước, điểm sau. */
class GuildShopRulesTest {

    private static final GuildShopItem PILL = new GuildShopItem(1L, 200L, 2);

    @Test
    void availableShouldBeContributionMinusSpent_neverNegative() {
        assertThat(GuildShopRules.available(1_000L, 300L)).isEqualTo(700L);
        assertThat(GuildShopRules.available(100L, 300L)).isZero();
    }

    @Test
    void shouldRefuseLowGuildLevelFirst_thenShortContribution() {
        assertThat(GuildShopRules.refusal(PILL, 1, 0L)).contains(GuildShopOutcome.GUILD_LEVEL_TOO_LOW);
        assertThat(GuildShopRules.refusal(PILL, 2, 199L)).contains(GuildShopOutcome.NOT_ENOUGH_CONTRIBUTION);
        assertThat(GuildShopRules.refusal(PILL, 2, 200L)).isEmpty();
    }
}
