package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuildAuctionRulesTest {

    @Test
    void minimumBidShouldStartAtStartPriceAndRaiseTenPercentRoundedUp() {
        assertThat(GuildAuctionRules.minimumBid(0L)).isEqualTo(100L);
        assertThat(GuildAuctionRules.minimumBid(100L)).isEqualTo(110L);
        assertThat(GuildAuctionRules.minimumBid(301L)).isEqualTo(332L);
    }

    @Test
    void shareShouldRoundDown() {
        assertThat(GuildAuctionRules.shareOf(301L, 3)).isEqualTo(100L);
        assertThat(GuildAuctionRules.shareOf(301L, 0)).isZero();
    }
}
