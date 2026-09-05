package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void shouldAddItem_whenBelowCapacity() {
        Inventory inventory = new Inventory(1L, 5, List.of());

        Inventory.AddResult result = inventory.add(ItemStack.newItem(100L, 1, 0), 1);

        assertThat(result).isInstanceOf(Inventory.Added.class);
        assertThat(inventory.items()).hasSize(1);
        assertThat(inventory.isFull()).isFalse();
    }

    @Test
    void shouldRejectAdd_whenAtCapacity() {
        Inventory inventory = new Inventory(1L, 1, List.of(ItemStack.newItem(100L, 1, 0)));

        Inventory.AddResult result = inventory.add(ItemStack.newItem(200L, 1, 0), 1);

        assertThat(result).isInstanceOf(Inventory.Full.class);
        assertThat(inventory.items()).hasSize(1); // không thêm item mới khi đầy
        assertThat(inventory.isFull()).isTrue();
    }

    /**
     * Luật của chủ dự án (2026-09-05): trang bị (vũ khí, quần áo...) KHÔNG xếp chồng; tiêu hao,
     * vật phẩm nhiệm vụ, vật phẩm sự kiện thì CÓ. Luật đó đã nằm sẵn ở cột
     * {@code item_template.max_stack} (mọi món có {@code equip_slot} là 1, còn lại 99) — nhóm test
     * này khoá phần code cuối cùng chưa đọc nó.
     */
    @Test
    void shouldMergeIntoExistingStack_whenItemIsStackable() {
        Inventory inventory = new Inventory(1L, 20, List.of(new ItemStack(7L, 100L, 15, 0)));

        Inventory.AddResult result = inventory.add(ItemStack.newItem(100L, 2, 0), 99);

        assertThat(result).isEqualTo(new Inventory.Added(0)); // gộp vào ô CŨ, không phải ô mới
        assertThat(inventory.items()).hasSize(1);
        assertThat(inventory.items().get(0).quantity()).isEqualTo(17);
        assertThat(inventory.items().get(0).id()).isEqualTo(7L); // giữ id thật của dòng DB
    }

    @Test
    void shouldTakeANewSlot_whenItemIsNotStackable() {
        // Trang bị: maxStack = 1 ⇒ cây kiếm thứ hai phải chiếm ô riêng.
        Inventory inventory = new Inventory(1L, 20, List.of(new ItemStack(7L, 100L, 1, 0)));

        Inventory.AddResult result = inventory.add(ItemStack.newItem(100L, 1, 0), 1);

        assertThat(result).isEqualTo(new Inventory.Added(1));
        assertThat(inventory.items()).hasSize(2);
    }

    @Test
    void shouldTakeANewSlot_whenMergingWouldExceedMaxStack() {
        // Không xé lẻ (xem javadoc Inventory.add): 98 + 5 > 99 ⇒ mở ô mới nguyên vẹn 5 cái.
        Inventory inventory = new Inventory(1L, 20, List.of(new ItemStack(7L, 100L, 98, 0)));

        Inventory.AddResult result = inventory.add(ItemStack.newItem(100L, 5, 0), 99);

        assertThat(result).isEqualTo(new Inventory.Added(1));
        assertThat(inventory.items().get(0).quantity()).isEqualTo(98);
        assertThat(inventory.items().get(1).quantity()).isEqualTo(5);
    }

    @Test
    void shouldNotMergeAcrossEnhanceLevels() {
        // Một cây +7 không phải cùng thứ với +0, dù cùng itemTemplateId.
        Inventory inventory = new Inventory(1L, 20, List.of(new ItemStack(7L, 100L, 1, 7)));

        Inventory.AddResult result = inventory.add(ItemStack.newItem(100L, 1, 0), 99);

        assertThat(result).isEqualTo(new Inventory.Added(1));
        assertThat(inventory.items()).hasSize(2);
    }

    @Test
    void shouldStillMerge_whenBagIsFull() {
        // Túi đầy KHÔNG chặn việc gộp: không cần ô mới thì không phải hỏi sức chứa.
        Inventory inventory = new Inventory(1L, 1, List.of(new ItemStack(7L, 100L, 3, 0)));

        Inventory.AddResult result = inventory.add(ItemStack.newItem(100L, 2, 0), 99);

        assertThat(result).isEqualTo(new Inventory.Added(0));
        assertThat(inventory.items().get(0).quantity()).isEqualTo(5);
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
