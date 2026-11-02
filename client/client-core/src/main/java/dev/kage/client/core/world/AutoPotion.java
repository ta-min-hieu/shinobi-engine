package dev.kage.client.core.world;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import dev.kage.protocol.ItemUseEffect;

/**
 * Tự uống đan (ADR-156) — công tắc trong {@code /caidat}, mặc định TẮT. Bật thì cứ nửa giây xét một lần: máu dưới
 * {@value #THRESHOLD_PERCENT}% (và còn sống) thì gửi ĐÚNG lệnh "dùng vật phẩm" người chơi bấm tay cho món hồi máu đầu
 * tiên trong túi. Không quyết định gì thay server: server vẫn kiểm sở hữu, hồi chiêu 10 giây, và cộng máu. Sau mỗi lần
 * gửi chờ 10 giây (đúng hồi chiêu đan) để không dội lệnh bị từ chối.
 *
 * <p>Gọi {@link #update} mỗi khung; phần lớn khung chỉ là một phép so sánh thời gian — danh sách túi chỉ được đọc
 * khi đã quyết uống.
 */
final class AutoPotion {

    static final int THRESHOLD_PERCENT = 35;
    static final long CHECK_INTERVAL_NANOS = 500_000_000L;
    static final long RETRY_AFTER_NANOS = 10_000_000_000L;

    private static final int PERCENT = 100;

    /** Công tắc + máu của chính mình — phía {@code WorldScreen} có, túi đồ thì không. */
    record Vitals(BooleanSupplier enabled, IntSupplier hp, IntSupplier maxHp) {}

    private final Vitals vitals;
    private final Supplier<List<InventoryController.Item>> items;
    private final LongFunction<ItemUseEffect> useEffectOf;
    private final LongConsumer useItem;
    private long nextCheckNanos;

    AutoPotion(
            Vitals vitals,
            Supplier<List<InventoryController.Item>> items,
            LongFunction<ItemUseEffect> useEffectOf,
            LongConsumer useItem) {
        this.vitals = vitals;
        this.items = items;
        this.useEffectOf = useEffectOf;
        this.useItem = useItem;
    }

    /** Một khung; {@code nowNanos} là đồng hồ đơn điệu ({@code System.nanoTime()}). */
    void update(long nowNanos) {
        if (nowNanos - nextCheckNanos < 0 || !vitals.enabled().getAsBoolean()) {
            return;
        }
        nextCheckNanos = nowNanos + CHECK_INTERVAL_NANOS;
        int hp = vitals.hp().getAsInt();
        int maxHp = vitals.maxHp().getAsInt();
        if (hp <= 0 || maxHp <= 0 || (long) hp * PERCENT >= (long) maxHp * THRESHOLD_PERCENT) {
            return;
        }
        for (InventoryController.Item item : items.get()) {
            if (useEffectOf.apply(item.itemTemplateId()) == ItemUseEffect.ITEM_USE_EFFECT_HEAL_HP) {
                useItem.accept(item.itemId());
                nextCheckNanos = nowNanos + RETRY_AFTER_NANOS;
                return;
            }
        }
    }
}
