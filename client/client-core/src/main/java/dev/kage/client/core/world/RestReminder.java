package dev.kage.client.core.world;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Nhắc nghỉ ngơi (ADR-174) — chơi liên tục {@value #FIRST_AFTER_HOURS} giờ thì in một dòng hệ thống nhắc nghỉ, sau đó
 * cứ mỗi giờ nhắc lại. Thuần client: đếm từ lúc vào thế giới bằng đồng hồ đơn điệu, không gửi gì lên server, không
 * chặn chơi. Công tắc trong {@code /caidat}, mặc định BẬT.
 *
 * <p>Gọi {@link #update} mỗi khung; phần lớn khung chỉ là một phép so sánh thời gian.
 */
final class RestReminder {

    static final int FIRST_AFTER_HOURS = 2;
    static final long HOUR_NANOS = 3_600_000_000_000L;

    private final IntFunction<String> message;
    private final Consumer<String> notice;
    private BooleanSupplier enabled = () -> true;
    private long startNanos;
    private long nextNanos;
    private boolean started;

    /** {@code message} dựng câu nhắc từ số giờ đã chơi; {@code notice} in nó ra (dòng hệ thống chat). */
    RestReminder(IntFunction<String> message, Consumer<String> notice) {
        this.message = message;
        this.notice = notice;
    }

    /** Nối công tắc {@code /caidat} (gọi lúc nối lớp hiển thị). */
    void useSwitch(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    /** Một khung; {@code nowNanos} là đồng hồ đơn điệu ({@code System.nanoTime()}). Khung đầu tiên = mốc bắt đầu. */
    void update(long nowNanos) {
        if (!started) {
            started = true;
            startNanos = nowNanos;
            nextNanos = nowNanos + FIRST_AFTER_HOURS * HOUR_NANOS;
            return;
        }
        if (nowNanos - nextNanos < 0) {
            return;
        }
        nextNanos += HOUR_NANOS;
        if (enabled.getAsBoolean()) {
            notice.accept(message.apply((int) ((nowNanos - startNanos) / HOUR_NANOS)));
        }
    }
}
