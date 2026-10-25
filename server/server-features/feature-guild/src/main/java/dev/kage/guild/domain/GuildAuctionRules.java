package dev.kage.guild.domain;

import java.time.Duration;

/** Luật đấu giá bang (ADR-112 D2) — hàm thuần. */
public final class GuildAuctionRules {

    /** Lô mở bao lâu kể từ lúc boss chết. */
    public static final Duration DURATION = Duration.ofMinutes(30);

    /** Giá khởi điểm (lần trả đầu phải ≥ số này). */
    public static final long START_PRICE = 100L;

    /** Mỗi lần trả phải hơn giá đang dẫn ít nhất chừng này phần trăm. */
    public static final int MIN_RAISE_PERCENT = 10;

    /** Nhịp chốt lô hết giờ + gửi hàng đợi thư. */
    public static final long SETTLE_PERIOD_SECONDS = 30L;

    private static final int PERCENT = 100;

    private GuildAuctionRules() {}

    /** Mức thấp nhất được trả khi giá đang dẫn là {@code currentBid} (0 = chưa ai trả). */
    public static long minimumBid(long currentBid) {
        if (currentBid <= 0) {
            return START_PRICE;
        }
        return currentBid + Math.max(1L, (currentBid * MIN_RAISE_PERCENT + PERCENT - 1) / PERCENT);
    }

    /** Phần chia đều (làm tròn xuống) cho mỗi người tham gia. */
    public static long shareOf(long winningBid, int participants) {
        return participants <= 0 ? 0L : winningBid / participants;
    }
}
