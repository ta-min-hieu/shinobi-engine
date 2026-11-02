package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ItemUseEffect;

/** ADR-156 — chỉ uống khi bật, còn sống, dưới ngưỡng, có món hồi máu; không dội lệnh trong hồi chiêu. */
final class AutoPotionTest {

    private static final long PILL_ITEM_ID = 42L;
    private static final long PILL_TEMPLATE = 7L;
    private static final long SWORD_TEMPLATE = 2L;

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger hp = new AtomicInteger(30);
    private final List<InventoryController.Item> bag = new ArrayList<>(List.of(
            new InventoryController.Item(1L, SWORD_TEMPLATE, 1, 0, 0),
            new InventoryController.Item(PILL_ITEM_ID, PILL_TEMPLATE, 3, 0, 1)));
    private final List<Long> used = new ArrayList<>();
    private final AutoPotion autoPotion = new AutoPotion(
            new AutoPotion.Vitals(enabled::get, hp::get, () -> 100),
            () -> bag,
            id -> id == PILL_TEMPLATE
                    ? ItemUseEffect.ITEM_USE_EFFECT_HEAL_HP
                    : ItemUseEffect.ITEM_USE_EFFECT_UNSPECIFIED,
            used::add);

    @Test
    void shouldDrinkBelowThreshold_thenWaitForThePillCooldown() {
        autoPotion.update(1_000L);
        assertThat(used).containsExactly(PILL_ITEM_ID);

        autoPotion.update(1_000L + AutoPotion.CHECK_INTERVAL_NANOS);
        assertThat(used).as("còn trong hồi chiêu đan").hasSize(1);

        autoPotion.update(1_000L + AutoPotion.RETRY_AFTER_NANOS);
        assertThat(used).hasSize(2);
    }

    @Test
    void shouldStayQuietWhenDisabledHealthyDeadOrWithoutPills() {
        enabled.set(false);
        autoPotion.update(1L);
        enabled.set(true);
        hp.set(AutoPotion.THRESHOLD_PERCENT);
        autoPotion.update(2L * AutoPotion.CHECK_INTERVAL_NANOS);
        hp.set(0);
        autoPotion.update(4L * AutoPotion.CHECK_INTERVAL_NANOS);
        hp.set(10);
        bag.remove(1);
        autoPotion.update(6L * AutoPotion.CHECK_INTERVAL_NANOS);

        assertThat(used).isEmpty();
    }
}
