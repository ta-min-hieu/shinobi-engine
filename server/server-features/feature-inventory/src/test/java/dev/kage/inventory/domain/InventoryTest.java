package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void shouldAddItem_whenBelowCapacity() {
        var inventory = new Inventory(1L, 5, List.of());

        var result = inventory.add(new ItemStack(100L, 1, 0));

        assertThat(result).isInstanceOf(Inventory.Added.class);
        assertThat(inventory.items()).hasSize(1);
        assertThat(inventory.isFull()).isFalse();
    }

    @Test
    void shouldRejectAdd_whenAtCapacity() {
        var inventory = new Inventory(1L, 1, List.of(new ItemStack(100L, 1, 0)));

        var result = inventory.add(new ItemStack(200L, 1, 0));

        assertThat(result).isInstanceOf(Inventory.Full.class);
        assertThat(inventory.items()).hasSize(1); // không thêm item mới khi đầy
        assertThat(inventory.isFull()).isTrue();
    }

    @Test
    void shouldRejectItemStack_whenQuantityNotPositive() {
        assertThatThrownBy(() -> new ItemStack(100L, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDropTableEntry_whenMaxQuantityBelowMin() {
        assertThatThrownBy(() -> new DropTableEntry(100L, 50, 5, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
