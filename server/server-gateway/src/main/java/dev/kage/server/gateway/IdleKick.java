package dev.kage.server.gateway;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.netty.channel.ChannelHandlerContext;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Tự ngắt khi treo máy (ADR-214) — mỗi kết nối một bản. Đang trong world mà quá {@code KAGE_IDLE_KICK_MINUTES} phút
 * (mặc định {@value #DEFAULT_MINUTES}; {@code 0} = tắt) không gửi gói nào ngoài {@code PING} ⇒ một dòng chat hệ
 * thống rồi đóng kết nối; {@code channelInactive} dọn nhân vật như mọi lần ngắt khác.
 *
 * <p>Khác {@code READER_IDLE_TIMEOUT} của Netty (kết nối CHẾT — không cả {@code PING}): ở đây kết nối còn sống, chỉ
 * người chơi không làm gì. Kiểm theo nhịp trên event loop của chính kết nối, không luồng riêng.
 */
final class IdleKick {

    static final String ENV = "KAGE_IDLE_KICK_MINUTES";
    static final int DEFAULT_MINUTES = 30;

    /** Nhịp kiểm tối đa — ngắt trễ nhất {@code timeout + nhịp}. */
    private static final Duration MAX_CHECK_PERIOD = Duration.ofSeconds(60);

    private static final Duration TIMEOUT = parse(System.getenv(ENV));

    private final Duration timeout;
    private volatile long lastActivityNanos = System.nanoTime();
    private ScheduledFuture<?> check;

    IdleKick() {
        this(TIMEOUT);
    }

    IdleKick(Duration timeout) {
        this.timeout = timeout;
    }

    /** Thiếu/rỗng ⇒ mặc định; số phút ≥ 0 (0 = tắt); giá trị lạ là lỗi cấu hình to tiếng (cùng khuôn GmConfig). */
    static Duration parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Duration.ofMinutes(DEFAULT_MINUTES);
        }
        String trimmed = raw.strip().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[0-9]{1,5}")) {
            throw new IllegalStateException(ENV + " phải là số phút (0 = tắt), nhận: " + raw);
        }
        return Duration.ofMinutes(Long.parseLong(trimmed));
    }

    /** Mọi gói client gửi tới — trừ {@code PING} (nhịp giữ kết nối, không phải người chơi). */
    void touch(int opcode) {
        if (opcode != Opcode.OPCODE_PING_VALUE) {
            lastActivityNanos = System.nanoTime();
        }
    }

    /** {@code true} nếu tới {@code nowNanos} đã im quá thời hạn (và tính năng đang bật). */
    boolean isIdle(long nowNanos) {
        return !timeout.isZero() && nowNanos - lastActivityNanos >= timeout.toNanos();
    }

    /** Bắt đầu kiểm theo nhịp; {@code inWorld} = đang có nhân vật trong world (màn chọn nhân vật không bị ngắt). */
    void start(ChannelHandlerContext ctx, BooleanSupplier inWorld) {
        if (timeout.isZero()) {
            return;
        }
        long period = Math.min(MAX_CHECK_PERIOD.toMillis(), Math.max(1L, timeout.toMillis() / 2));
        check = ctx.executor().scheduleAtFixedRate(() -> checkNow(ctx, inWorld), period, period, TimeUnit.MILLISECONDS);
    }

    /** Dừng kiểm (kết nối đã đóng). */
    void stop() {
        ScheduledFuture<?> scheduled = check;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    private void checkNow(ChannelHandlerContext ctx, BooleanSupplier inWorld) {
        if (!ctx.channel().isActive() || !inWorld.getAsBoolean() || !isIdle(System.nanoTime())) {
            return;
        }
        ChatMessageEvent notice = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Treo máy quá " + timeout.toMinutes() + " phút — tự ngắt kết nối. / Idle for over "
                        + timeout.toMinutes() + " minutes — disconnected.")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, ProtocolVersion.CURRENT, 0);
        ctx.writeAndFlush(new PacketCodec.Frame(header, notice.toByteArray()));
        ctx.close();
    }
}
