package dev.kage.server.database;

/**
 * Bên nhận thông báo "vàng vừa đổi và đã commit" — {@code server-gateway} nối vào bằng
 * {@code ServerDatabaseBootstrap#configureGoldChangeListener} để biến thành
 * {@code GoldChangedEvent} (ADR-035 Decision-3).
 *
 * <p>Cùng khuôn {@link DbMetricsPort}: hạ tầng khai báo cổng, cấu hình MỘT lần lúc khởi động, mặc
 * định {@link #NO_OP} nên mọi test/call site chưa cấu hình vẫn chạy y như trước.
 *
 * <p>Bị gọi trên chính Virtual Thread vừa chạy transaction (SAU {@code commit()}), KHÔNG phải trên
 * game tick hay Netty EventLoop — implementation phải rẻ và không được ném exception ra ngoài, vì
 * lúc này hiệu ứng thật đã nằm trong DB rồi, ném ở đây chỉ làm caller tưởng giao dịch hỏng.
 */
@FunctionalInterface
public interface GoldChangeListener {

    /** Không ai nghe — dùng cho test và cho giai đoạn khởi động trước khi gateway kịp nối vào. */
    GoldChangeListener NO_OP = change -> {};

    void onGoldChanged(GoldChange change);
}
