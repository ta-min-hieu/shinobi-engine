package dev.kage.server.gateway;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Hồi chiêu dùng vật phẩm của MỘT kết nối (quyết định chủ dự án 2026-10-11: 10 giây).
 *
 * <p>Trước đó đan dược uống liên tục được: mỗi viên hồi 50 máu ngay lập tức, nên giữa trận chỉ cần đủ
 * đan là không bao giờ chết — số viên trong túi là thanh máu thứ hai không giới hạn tốc độ.
 *
 * <p><b>Giữ chỗ trước, hoàn lại nếu hỏng.</b> Lệnh dùng vật phẩm chạm DB trên luồng khác, nên "kiểm rồi
 * mới ghi mốc khi thành công" để lọt hai lệnh gửi sát nhau (cả hai cùng thấy hết hồi chiêu). {@link
 * #tryBegin} vì thế chiếm hồi chiêu NGAY bằng compare-and-set; lệnh thất bại (không có món, món không
 * dùng được) gọi {@link #cancel} trả lại, để một lần bấm hỏng không phạt người chơi 10 giây.
 *
 * <p>Theo kết nối chứ không theo nhân vật: đăng nhập lại là hết hồi chiêu. Chấp nhận được — đăng nhập
 * lại vốn đã hồi đầy máu (máu không được lưu), nên không mở thêm đường nào.
 */
final class ItemUseCooldown {

    static final Duration DURATION = Duration.ofSeconds(10);

    private final LongSupplier nanoClock;
    /** Mốc {@code nanoTime} từ đó được dùng lần kế. */
    private final AtomicLong readyAtNanos;

    ItemUseCooldown() {
        this(System::nanoTime);
    }

    /** Seam cho test: đồng hồ điều khiển được. */
    ItemUseCooldown(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.readyAtNanos = new AtomicLong(nanoClock.getAsLong());
    }

    /**
     * Chiếm hồi chiêu nếu đã hết.
     *
     * @return vé để {@link #cancel} hoàn lại nếu lệnh thất bại; {@link #ON_COOLDOWN} nếu còn hồi chiêu
     */
    long tryBegin() {
        long now = nanoClock.getAsLong();
        long readyAt = readyAtNanos.get();
        // So bằng hiệu: nanoTime được phép tràn, so trực tiếp hai mốc là sai.
        if (now - readyAt < 0) {
            return ON_COOLDOWN;
        }
        long next = now + DURATION.toNanos();
        return readyAtNanos.compareAndSet(readyAt, next) ? next : ON_COOLDOWN;
    }

    /** Hoàn lại lượt đã chiếm bằng {@code ticket} — chỉ khi chưa lệnh nào khác chiếm sau đó. */
    void cancel(long ticket) {
        readyAtNanos.compareAndSet(ticket, nanoClock.getAsLong());
    }

    /** Giá trị {@link #tryBegin} trả khi còn hồi chiêu. Không trùng vé thật: vé luôn là một mốc tương lai. */
    static final long ON_COOLDOWN = Long.MIN_VALUE;
}
