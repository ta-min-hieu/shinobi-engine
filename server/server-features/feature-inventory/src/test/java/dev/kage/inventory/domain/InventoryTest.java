package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void shouldAddItem_whenBelowCapacity() {
        Inventory inventory = new Inventory(1L, 5, List.of());

        Inventory.AddResult result = inventory.add(ItemStack.newItem(100L, 1, 0));

        assertThat(result).isInstanceOf(Inventory.Added.class);
        assertThat(inventory.items()).hasSize(1);
        assertThat(inventory.isFull()).isFalse();
    }

    @Test
    void shouldRejectAdd_whenAtCapacity() {
        Inventory inventory = new Inventory(1L, 1, List.of(ItemStack.newItem(100L, 1, 0)));

        Inventory.AddResult result = inventory.add(ItemStack.newItem(200L, 1, 0));

        assertThat(result).isInstanceOf(Inventory.Full.class);
        assertThat(inventory.items()).hasSize(1); // không thêm item mới khi đầy
        assertThat(inventory.isFull()).isTrue();
    }

    @Test
    void shouldRejectItemStack_whenQuantityNotPositive() {
        assertThatThrownBy(() -> ItemStack.newItem(100L, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDropTableEntry_whenMaxQuantityBelowMin() {
        assertThatThrownBy(() -> new DropTableEntry(100L, 50, 5, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFindItem_byRealId() {
        ItemStack stack = new ItemStack(42L, 100L, 1, 0);
        Inventory inventory = new Inventory(1L, 5, List.of(stack));

        assertThat(inventory.findItem(42L)).contains(stack);
        assertThat(inventory.findItem(999L)).isEmpty();
    }

    @Test
    void shouldReplaceItem_whenIdMatchesExistingItem() {
        Inventory inventory = new Inventory(1L, 5, List.of(new ItemStack(42L, 100L, 1, 3)));

        inventory.replaceItem(new ItemStack(42L, 100L, 1, 4));

        assertThat(inventory.findItem(42L)).contains(new ItemStack(42L, 100L, 1, 4));
    }

    @Test
    void shouldRejectReplaceItem_whenIdDoesNotExist() {
        Inventory inventory = new Inventory(1L, 5, List.of());

        assertThatThrownBy(() -> inventory.replaceItem(new ItemStack(42L, 100L, 1, 4)))
                .isInstanceOf(IllegalStateException.class);
    }
}
