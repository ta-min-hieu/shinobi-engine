package dev.kage.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ShopListingTest {

    @Test
    void shouldRejectNonPositiveItemTemplateId() {
        assertThatThrownBy(() -> new ShopListing(0, 5, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativePrices() {
        assertThatThrownBy(() -> new ShopListing(1, -1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShopListing(1, 5, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowZeroPrice() {
        assertThatCode(() -> new ShopListing(1, 0, 0)).doesNotThrowAnyException();
    }

    @Test
    void zeroBuyPriceMeansTheShopOnlyBuysItBack() {
        assertThat(new ShopListing(1, 0, 10).soldByShop()).isFalse();
        assertThat(new ShopListing(1, 5, 2).soldByShop()).isTrue();
    }

    @Test
    void shouldMultiplyPriceByQuantity() {
        ShopListing listing = new ShopListing(1, 80, 20);

        assertThat(listing.totalBuyCost(3)).isEqualTo(240L);
        assertThat(listing.totalSellCredit(3)).isEqualTo(60L);
    }

    /** Phép nhân thường tràn thành số ÂM — trừ số âm là cộng vàng (README dòng 25(iv)). */
    @Test
    void shouldSaturateInsteadOfWrappingNegative_whenTotalOverflowsLong() {
        ShopListing listing = new ShopListing(1, Long.MAX_VALUE / 2, Long.MAX_VALUE / 2);

        assertThat(listing.totalBuyCost(Integer.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
        assertThat(listing.totalSellCredit(3)).isEqualTo(Long.MAX_VALUE);
    }
}
