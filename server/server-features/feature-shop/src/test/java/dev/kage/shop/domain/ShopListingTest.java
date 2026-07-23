package dev.kage.shop.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

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
}
