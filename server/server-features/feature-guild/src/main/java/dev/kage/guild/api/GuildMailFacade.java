package dev.kage.guild.api;

import java.time.Instant;

import dev.kage.guild.domain.GuildMailQuotaPort;

/**
 * Hạn mức thư toàn bang (ADR-235): mỗi bang tối đa {@value #DAILY_LIMIT} đợt mỗi ngày (theo mốc reset ngày). Việc gửi
 * thư thật do gateway làm qua facade thư. Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class GuildMailFacade {

    /** Số đợt thư toàn bang mỗi bang mỗi ngày. PLACEHOLDER cân bằng (Điều 27). */
    public static final int DAILY_LIMIT = 3;

    /** Không giới hạn, không ghi (composition test). */
    public static final GuildMailFacade DISABLED = new GuildMailFacade(new GuildMailQuotaPort() {
        @Override
        public int sentSince(long guildId, Instant since) {
            return 0;
        }

        @Override
        public void record(long guildId, long senderId, int recipients, Instant at) {
            // không lưu
        }
    });

    private final GuildMailQuotaPort port;

    /** Dựng ở composition root. */
    public GuildMailFacade(GuildMailQuotaPort port) {
        this.port = port;
    }

    /** Số đợt còn được gửi hôm nay (mốc đầu ngày {@code dayStart}). */
    public int remaining(long guildId, Instant dayStart) {
        return Math.max(0, DAILY_LIMIT - port.sentSince(guildId, dayStart));
    }

    /** Ghi một đợt vừa gửi. */
    public void record(long guildId, long senderId, int recipients, Instant at) {
        port.record(guildId, senderId, recipients, at);
    }
}
