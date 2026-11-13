package dev.kage.client.core.world;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Xác nhận bán đồ đã cường hoá (ADR-349) — món +1 trở lên phải bấm Bán lần hai trong {@value #WINDOW_MILLIS} ms
 * mới gửi lệnh; lần đầu chỉ hiện lời nhắc. Món chưa cường hoá bán ngay như cũ. Thuần client: server không đổi.
 */
final class SellConfirmGuard {

    /** Thời gian chờ bấm lần hai. */
    static final long WINDOW_MILLIS = 3_000L;

    private final LongSupplier clockMillis;
    private final Consumer<InventoryController.Item> ask;
    private long pendingItemId;
    private long pendingAtMillis;
    private boolean pending;

    /** {@code ask} hiện lời nhắc "bấm lần nữa" cho món đó. */
    SellConfirmGuard(LongSupplier clockMillis, Consumer<InventoryController.Item> ask) {
        this.clockMillis = clockMillis;
        this.ask = ask;
    }

    /** {@code true} nếu được bán ngay (chưa cường hoá, hoặc đây là lần bấm xác nhận). */
    boolean confirm(InventoryController.Item item) {
        if (item.enhanceLevel() <= 0) {
            return true;
        }
        long now = clockMillis.getAsLong();
        if (pending && pendingItemId == item.itemId() && now - pendingAtMillis <= WINDOW_MILLIS) {
            pending = false;
            return true;
        }
        pending = true;
        pendingItemId = item.itemId();
        pendingAtMillis = now;
        ask.accept(item);
        return false;
    }
}
