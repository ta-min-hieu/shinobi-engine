package dev.kage.pvp.domain;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Luật mùa Luận Kiếm (ADR-087) — hàm thuần, tham số cân bằng ở một chỗ. */
public final class PvpSeasonPolicy {

    /** Một mùa dài 4 tuần. */
    public static final Duration SEASON_LENGTH = Duration.ofDays(28);

    private PvpSeasonPolicy() {}

    /** Elo đầu mùa mới: kéo nửa đường về mốc {@link EloRating#DEFAULT_RATING} (1200 ⇒ 1100, 900 ⇒ 950). */
    public static int softReset(int rating) {
        return EloRating.DEFAULT_RATING + (rating - EloRating.DEFAULT_RATING) / 2;
    }

    /** Bậc thưởng đầu tiên phủ {@code rank}; rỗng nếu không bậc nào phủ. */
    public static Optional<PvpSeasonPort.Reward> rewardFor(int rank, List<PvpSeasonPort.Reward> tiers) {
        return tiers.stream()
                .filter(tier -> rank >= tier.rankFrom() && rank <= tier.rankTo())
                .findFirst();
    }
}
