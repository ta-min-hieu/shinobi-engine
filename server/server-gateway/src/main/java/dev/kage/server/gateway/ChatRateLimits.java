package dev.kage.server.gateway;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import dev.kage.protocol.ChatChannel;

/**
 * Trần tần suất chat theo TÀI KHOẢN, sống qua các lần ngắt/nối lại (2026-10-10).
 *
 * <p><b>Vì sao không để trong {@code GatewayChannelHandler} như các trần khác.</b> Trần di chuyển/
 * chiến đấu/lệnh chạm DB bảo vệ TÀI NGUYÊN của server trước một kết nối — kết nối mới thì sổ mới là
 * đúng. Trần chat bảo vệ NGƯỜI CHƠI KHÁC trước một người: để theo kết nối thì hết lượt chỉ cần ngắt
 * rồi nối lại là có sổ trắng, và hình phạt "vượt liên tục thì ngắt kết nối" hoá thành phần thưởng
 * (xem {@code ChatRateLimitReconnectTest}).
 *
 * <p>Khoá theo tài khoản chứ không theo nhân vật: đổi nhân vật trên cùng tài khoản cũng không được
 * thêm lượt — đúng như lúc bộ đếm còn nằm ở kết nối.
 *
 * <p><b>Luồng.</b> {@link #acquire} được gọi trên EventLoop của kết nối gửi tin. Hai kết nối của cùng
 * một tài khoản (hai nhân vật, hoặc phiên cũ đang bị đá) nằm trên hai EventLoop khác nhau, nên mỗi sổ
 * tự đồng bộ. Phần trong khoá chỉ là vài phép cộng — không I/O, không chờ ai.
 *
 * <p><b>Bộ nhớ.</b> Sổ nào im lặng quá {@link #IDLE_BEFORE_FORGOTTEN} thì bị quên. An toàn vì
 * {@link RateLimiter} tự về trạng thái trắng sau hai cửa sổ không có lệnh nào (xem
 * {@code rotateWindowIfElapsed}) — sổ bị quên và sổ mới tạo lại cho ra cùng một phán quyết.
 */
final class ChatRateLimits {

    /** Phán quyết cho một tin nhắn sắp gửi. */
    enum Verdict {
        ALLOWED,
        /** Vượt trần cửa sổ này — từ chối tin, giữ kết nối. */
        REJECTED,
        /** Vượt trần nhiều cửa sổ LIÊN TIẾP — từ chối tin và ngắt kết nối. */
        REJECTED_AND_DISCONNECT
    }

    // NETWORK.md §13 / GAMEPLAY.md §10 — World nghiêm ngặt nhất (toàn server thấy), Khu vực và Nhóm
    // lỏng nhất (ít người thấy), Riêng tư và Bang hội ở giữa. Số giữ nguyên từ lúc còn theo kết nối.
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final int WORLD_PER_WINDOW = 5;
    private static final int PRIVATE_PER_WINDOW = 10;
    private static final int AREA_PER_WINDOW = 15;
    private static final int GUILD_PER_WINDOW = 10;
    private static final int PARTY_PER_WINDOW = 15;

    // Dài hơn hai cửa sổ (mốc RateLimiter tự trắng lại) — xem javadoc lớp, mục "Bộ nhớ".
    private static final Duration IDLE_BEFORE_FORGOTTEN = WINDOW.multipliedBy(3);
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final Map<Long, AccountLimiters> limitersByAccountId = new ConcurrentHashMap<>();
    private final LongSupplier nanoTimeSource;
    private volatile long lastSweepNanos;

    ChatRateLimits() {
        this(System::nanoTime);
    }

    /** Constructor cho test — {@code nanoTimeSource} giả để không phụ thuộc thời gian thật trôi. */
    ChatRateLimits(LongSupplier nanoTimeSource) {
        this.nanoTimeSource = nanoTimeSource;
        this.lastSweepNanos = nanoTimeSource.getAsLong();
    }

    /**
     * Đếm một tin nhắn của {@code accountId} vào trần của {@code channel}.
     *
     * @param channel kênh trên dây; giá trị lạ/không xác định tính vào trần kênh Thế giới (nghiêm
     *     ngặt nhất) — gói sai kênh không được là đường gửi miễn phí
     */
    Verdict acquire(long accountId, ChatChannel channel) {
        long now = nanoTimeSource.getAsLong();
        forgetIdleAccountsIfDue(now);
        return limitersByAccountId
                .computeIfAbsent(accountId, id -> new AccountLimiters(nanoTimeSource))
                .acquire(channel, now);
    }

    /** Số tài khoản đang có sổ — để test kiểm sổ cũ thật sự bị quên. */
    int trackedAccountCount() {
        return limitersByAccountId.size();
    }

    private void forgetIdleAccountsIfDue(long now) {
        if (now - lastSweepNanos < SWEEP_INTERVAL.toNanos()) {
            return;
        }
        // Hai luồng cùng thấy "tới hạn" thì cùng quét — thừa một lượt, không sai gì.
        lastSweepNanos = now;
        long idleNanos = IDLE_BEFORE_FORGOTTEN.toNanos();
        limitersByAccountId.values().removeIf(limiters -> limiters.idleSince(now) > idleNanos);
    }

    /** Năm bộ đếm của một tài khoản. */
    private static final class AccountLimiters {

        private final RateLimiter world;
        private final RateLimiter privateMessages;
        private final RateLimiter area;
        private final RateLimiter guild;
        private final RateLimiter party;
        private long lastUsedNanos;

        AccountLimiters(LongSupplier nanoTimeSource) {
            this.world = new RateLimiter(WORLD_PER_WINDOW, WINDOW, nanoTimeSource);
            this.privateMessages = new RateLimiter(PRIVATE_PER_WINDOW, WINDOW, nanoTimeSource);
            this.area = new RateLimiter(AREA_PER_WINDOW, WINDOW, nanoTimeSource);
            this.guild = new RateLimiter(GUILD_PER_WINDOW, WINDOW, nanoTimeSource);
            this.party = new RateLimiter(PARTY_PER_WINDOW, WINDOW, nanoTimeSource);
            this.lastUsedNanos = nanoTimeSource.getAsLong();
        }

        synchronized Verdict acquire(ChatChannel channel, long now) {
            lastUsedNanos = now;
            RateLimiter limiter = limiterFor(channel);
            if (limiter.tryAcquire()) {
                return Verdict.ALLOWED;
            }
            return limiter.shouldDisconnect() ? Verdict.REJECTED_AND_DISCONNECT : Verdict.REJECTED;
        }

        synchronized long idleSince(long now) {
            return now - lastUsedNanos;
        }

        private RateLimiter limiterFor(ChatChannel channel) {
            return switch (channel) {
                case CHAT_CHANNEL_PRIVATE -> privateMessages;
                case CHAT_CHANNEL_AREA -> area;
                case CHAT_CHANNEL_GUILD -> guild;
                case CHAT_CHANNEL_PARTY -> party;
                default -> world; // WORLD/UNSPECIFIED/UNRECOGNIZED — nghiêm ngặt nhất.
            };
        }
    }
}
