package dev.kage.guild.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cấp bang, cống hiến, nhiệm vụ tuần (ADR-068/069). Mỗi hàm GHI là một transaction trọn vẹn trong adapter
 * (server-database) — chạm vàng ({@code character_stat}) nên không làm bằng {@link GuildPort}.
 */
public interface GuildProgressionPort {

    /** Bang của {@code characterId} kèm tiến độ tuần {@code weekStart}; rỗng nếu không ở bang nào. */
    Optional<GuildStanding> standingOf(long characterId, LocalDate weekStart);

    /**
     * Kết quả góp.
     *
     * @param leveledUp lần góp này làm bang lên cấp
     * @param guildId bang nhận (0 nếu không ở bang nào)
     */
    record Donation(DonationOutcome outcome, boolean leveledUp, long guildId) {}

    /** Trừ vàng, cộng kinh nghiệm bang + cống hiến, audit — nguyên tử, có khoá chống trùng. */
    Donation donate(long characterId, long amount, String idempotencyKey);

    /** Cộng một lượt hạ quái vào tuần {@code weekStart}; có giá trị đúng lần chạm mốc. */
    Optional<WeeklyCompletion> recordKill(long characterId, LocalDate weekStart);

    /** Cống hiến từng thành viên của bang: {@code characterId → điểm}. */
    Map<Long, Long> contributionsOf(long guildId);

    /** Mọi bang, kinh nghiệm giảm dần rồi id tăng dần. */
    List<GuildRank> rankGuilds();
}
