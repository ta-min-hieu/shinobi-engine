package dev.kage.shop.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OwnedItemTest {

    @Test
    void shouldRejectNonPositiveItemId() {
        assertThatThrownBy(() -> new OwnedItem(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveItemTemplateId() {
        assertThatThrownBy(() -> new OwnedItem(1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        assertThatThrownBy(() -> new OwnedItem(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
