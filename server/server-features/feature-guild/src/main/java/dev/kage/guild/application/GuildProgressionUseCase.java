package dev.kage.guild.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.guild.domain.DonationOutcome;
import dev.kage.guild.domain.GuildProgressionPort;
import dev.kage.guild.domain.GuildRank;
import dev.kage.guild.domain.GuildStanding;
import dev.kage.guild.domain.GuildWeeklyQuest;
import dev.kage.guild.domain.WeeklyCompletion;

/** Cấp bang + cống hiến + nhiệm vụ tuần (ADR-068/069). Luật số lượng góp ở đây; ghi nguyên tử ở port. */
public final class GuildProgressionUseCase {

    /** Góp theo bội số này (ADR-068 D2). */
    public static final long DONATION_STEP = 1_000L;

    public static final long MAX_DONATION = 100_000L;

    private final GuildProgressionPort port;
    private final Clock clock;

    public GuildProgressionUseCase(GuildProgressionPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public Optional<GuildStanding> standingOf(long characterId) {
        return port.standingOf(characterId, GuildWeeklyQuest.weekStart(clock.instant()));
    }

    /** Số lượng sai luật bị từ chối TRƯỚC khi chạm DB (không đốt khoá chống trùng). */
    public GuildProgressionPort.Donation donate(long characterId, long amount, String idempotencyKey) {
        if (amount < DONATION_STEP || amount > MAX_DONATION || amount % DONATION_STEP != 0) {
            return new GuildProgressionPort.Donation(DonationOutcome.INVALID_AMOUNT, false, 0L);
        }
        return port.donate(characterId, amount, idempotencyKey);
    }

    public Optional<WeeklyCompletion> recordKill(long characterId) {
        return port.recordKill(characterId, GuildWeeklyQuest.weekStart(clock.instant()));
    }

    public Map<Long, Long> contributionsOf(long guildId) {
        return port.contributionsOf(guildId);
    }

    public List<GuildRank> rankGuilds() {
        return port.rankGuilds();
    }
}
