package dev.kage.guild.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import dev.kage.guild.domain.GuildCheckInPort;

/** Điểm danh bang (ADR-186) — ngày tính theo giờ Việt Nam (như điểm danh cá nhân). Chạm DB — chỉ Virtual Thread. */
public final class GuildCheckInFacade {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Composition không có điểm danh bang (test). */
    public static final GuildCheckInFacade DISABLED = new GuildCheckInFacade(
            (characterId, day, at) -> new GuildCheckInPort.CheckIn(GuildCheckInPort.Outcome.NOT_IN_GUILD, 0, false, 0L),
            Clock.systemUTC());

    private final GuildCheckInPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public GuildCheckInFacade(GuildCheckInPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** Điểm danh hôm nay. */
    public GuildCheckInPort.CheckIn checkIn(long characterId) {
        return port.checkIn(characterId, LocalDate.now(clock.withZone(VIETNAM)), clock.instant());
    }
}
