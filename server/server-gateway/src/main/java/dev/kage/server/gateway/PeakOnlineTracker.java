package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Số người online cao nhất trong NGÀY (ADR-377) — RAM, đặt lại lúc 00:00 giờ Việt Nam. Không ghi DB: khởi động lại
 * server thì đếm lại từ đầu (ghi rõ trong ADR). Gọi từ event loop Netty mỗi lần nhân vật vào world, và từ Virtual
 * Thread khi hỏi — đồng bộ hoá bằng {@code synchronized}, chi phí một phép so sánh.
 */
final class PeakOnlineTracker {

    /** Múi giờ ngày của game (cùng mốc reset nhiệm vụ ngày). */
    static final ZoneId GAME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Ảnh chụp: đỉnh hôm nay và lúc đạt. */
    record Peak(int peak, Instant at) {}

    private final Clock clock;
    private LocalDate day;
    private int peak;
    private Instant peakAt;

    PeakOnlineTracker(Clock clock) {
        this.clock = clock;
        this.peakAt = clock.instant();
        this.day = LocalDate.ofInstant(peakAt, GAME_ZONE);
    }

    /** Báo số người đang online; nâng đỉnh nếu vượt. */
    synchronized void observe(int online) {
        Instant now = clock.instant();
        rollDay(now);
        if (online > peak) {
            peak = online;
            peakAt = now;
        }
    }

    /** Đỉnh hôm nay (sau khi đã cân với số hiện tại {@code online}). */
    synchronized Peak today(int online) {
        observe(online);
        return new Peak(peak, peakAt);
    }

    private void rollDay(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, GAME_ZONE);
        if (!today.equals(day)) {
            day = today;
            peak = 0;
            peakAt = now;
        }
    }
}
