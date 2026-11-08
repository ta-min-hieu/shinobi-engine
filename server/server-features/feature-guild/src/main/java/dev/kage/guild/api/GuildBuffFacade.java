package dev.kage.guild.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.guild.domain.GuildBuffPort;

/**
 * Buff bang (ADR-239): bang chủ tiêu {@value #COST} kinh nghiệm bang ⇒ mọi thành viên +{@value #PERCENT}% EXP đánh quái
 * trong 1 giờ. Hạn đọc qua bộ nhớ đệm {@value #CACHE_SECONDS} giây theo nhân vật — hạ quái không chạm DB mỗi lần.
 */
public final class GuildBuffFacade {

    /** Kinh nghiệm bang mỗi lần bật. PLACEHOLDER cân bằng (Điều 27). */
    public static final long COST = 1_000L;

    /** Phần trăm EXP cộng thêm. PLACEHOLDER cân bằng (Điều 27). */
    public static final int PERCENT = 20;

    /** Thời gian buff. */
    public static final Duration DURATION = Duration.ofHours(1);

    /** Tuổi tối đa của một mục đệm (giây). */
    public static final int CACHE_SECONDS = 30;

    /** Không bang nào có buff (composition test). */
    public static final GuildBuffFacade DISABLED = new GuildBuffFacade(new GuildBuffPort() {
        @Override
        public Activation activate(long leaderId, long cost, Instant until, Instant now) {
            return new Activation(Outcome.NOT_IN_GUILD, Instant.EPOCH, 0L, 0L);
        }

        @Override
        public Optional<Instant> buffUntilOf(long characterId) {
            return Optional.empty();
        }
    });

    private static final long HUNDRED = 100L;

    /** Một mục đệm: hạn buff + lúc đọc. */
    private record Cached(Instant until, Instant checkedAt) {}

    private final GuildBuffPort port;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    /** Dựng ở composition root. */
    public GuildBuffFacade(GuildBuffPort port) {
        this.port = port;
    }

    /** Bang chủ bật buff lúc {@code now}. OK ⇒ xoá đệm để mọi thành viên thấy ngay. */
    public GuildBuffPort.Activation activate(long leaderId, Instant now) {
        GuildBuffPort.Activation result = port.activate(leaderId, COST, now.plus(DURATION), now);
        if (result.outcome() == GuildBuffPort.Outcome.OK) {
            cache.clear();
        }
        return result;
    }

    /** Số giây buff còn lại của bang người này (0 nếu không có). Đọc thẳng DB. */
    public long secondsLeft(long characterId, Instant now) {
        Instant until = port.buffUntilOf(characterId).orElse(Instant.EPOCH);
        return now.isBefore(until) ? Duration.between(now, until).toSeconds() : 0L;
    }

    /** {@code amount} EXP đánh quái sau buff bang (làm tròn xuống) nếu bang người này đang có buff. */
    public long boosted(long characterId, long amount, Instant now) {
        Cached cached = cache.get(characterId);
        if (cached == null || cached.checkedAt().plusSeconds(CACHE_SECONDS).isBefore(now)) {
            cached = new Cached(port.buffUntilOf(characterId).orElse(Instant.EPOCH), now);
            cache.put(characterId, cached);
        }
        return now.isBefore(cached.until()) ? amount * (HUNDRED + PERCENT) / HUNDRED : amount;
    }
}
