package dev.kage.guild.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.guild.application.GuildProgressionUseCase;
import dev.kage.guild.domain.DonationOutcome;
import dev.kage.guild.domain.GuildProgressionPort;
import dev.kage.guild.domain.GuildRank;
import dev.kage.guild.domain.GuildStanding;
import dev.kage.guild.domain.WeeklyCompletion;

/** API cấp bang + cống hiến + nhiệm vụ tuần (ADR-068/069). */
public final class GuildProgressionFacade {

    /** Không có bang nào lên cấp (composition test). */
    public static final GuildProgressionFacade DISABLED =
            new GuildProgressionFacade(new GuildProgressionUseCase(new NoProgression(), Clock.systemUTC()));

    private final GuildProgressionUseCase useCase;

    /** Dựng ở composition root. */
    public GuildProgressionFacade(GuildProgressionUseCase useCase) {
        this.useCase = useCase;
    }

    /** Bang của {@code characterId} kèm tiến độ tuần này; rỗng nếu không ở bang nào. */
    public Optional<GuildStanding> standingOf(long characterId) {
        return useCase.standingOf(characterId);
    }

    /** Góp linh thạch (bội số 1.000) — nguyên tử, có khoá chống trùng. */
    public GuildProgressionPort.Donation donate(long characterId, long amount, String idempotencyKey) {
        return useCase.donate(characterId, amount, idempotencyKey);
    }

    /** Lượt hạ quái của {@code characterId}; có giá trị đúng lần bang chạm mốc tuần. */
    public Optional<WeeklyCompletion> recordKill(long characterId) {
        return useCase.recordKill(characterId);
    }

    /** Cống hiến từng thành viên của bang. */
    public Map<Long, Long> contributionsOf(long guildId) {
        return useCase.contributionsOf(guildId);
    }

    /** Mọi bang, kinh nghiệm giảm dần. */
    public List<GuildRank> rankGuilds() {
        return useCase.rankGuilds();
    }

    /** Không ai ở bang nào. */
    private static final class NoProgression implements GuildProgressionPort {
        @Override
        public Optional<GuildStanding> standingOf(long characterId, LocalDate weekStart) {
            return Optional.empty();
        }

        @Override
        public Donation donate(long characterId, long amount, String idempotencyKey) {
            return new Donation(DonationOutcome.NOT_IN_GUILD, false, 0L);
        }

        @Override
        public Optional<WeeklyCompletion> recordKill(long characterId, LocalDate weekStart) {
            return Optional.empty();
        }

        @Override
        public Map<Long, Long> contributionsOf(long guildId) {
            return Map.of();
        }

        @Override
        public List<GuildRank> rankGuilds() {
            return List.of();
        }
    }
}
