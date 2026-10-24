package dev.kage.pvp.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.pvp.domain.PvpSeasonPolicy;
import dev.kage.pvp.domain.PvpSeasonPort;

/** API mùa Luận Kiếm (ADR-087). Chạm DB — chỉ gọi trên luồng nền. */
public final class PvpSeasonFacade {

    /** Composition không có mùa (test). */
    public static final PvpSeasonFacade DISABLED = new PvpSeasonFacade(new PvpSeasonPort() {
        @Override
        public Optional<Season> currentSeason() {
            return Optional.empty();
        }

        @Override
        public boolean closeSeason(int number, Instant now, Duration nextLength) {
            return false;
        }

        @Override
        public List<Payout> pendingPayouts() {
            return List.of();
        }

        @Override
        public void markPaid(Payout payout) {
            // không có DB — không có gì để đánh dấu
        }

        @Override
        public List<Reward> rewards() {
            return List.of();
        }
    });

    private final PvpSeasonPort port;

    /** Dựng ở composition root. */
    public PvpSeasonFacade(PvpSeasonPort port) {
        this.port = port;
    }

    /** Mùa đang mở. */
    public Optional<PvpSeasonPort.Season> currentSeason() {
        return port.currentSeason();
    }

    /** Chốt mùa {@code number} nếu nó còn mở và mở mùa kế dài {@link PvpSeasonPolicy#SEASON_LENGTH}. */
    public boolean closeSeason(int number, Instant now) {
        return port.closeSeason(number, now, PvpSeasonPolicy.SEASON_LENGTH);
    }

    /** Phần thưởng còn chờ gửi. */
    public List<PvpSeasonPort.Payout> pendingPayouts() {
        return port.pendingPayouts();
    }

    /** Thư của {@code payout} đã gửi. */
    public void markPaid(PvpSeasonPort.Payout payout) {
        port.markPaid(payout);
    }

    /** Bậc thưởng theo hạng. */
    public List<PvpSeasonPort.Reward> rewards() {
        return port.rewards();
    }
}
