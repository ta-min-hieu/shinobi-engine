package dev.kage.pvp.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Mùa Luận Kiếm (ADR-087) — hiện thực ở server-database ({@code pvp_season}, {@code pvp_season_reward}, V82). */
public interface PvpSeasonPort {

    /**
     * Mùa đang mở.
     *
     * @param number số thứ tự mùa (từ 1)
     * @param endsAt lúc mùa hết hạn
     */
    record Season(int number, Instant endsAt) {}

    /**
     * Một phần thưởng mùa còn chờ gửi — chỉ người đã đánh ít nhất một trận.
     *
     * @param season mùa
     * @param characterId nhân vật
     * @param rank hạng lúc chốt (từ 1)
     * @param rating Elo LÚC CHỐT (trước khi kéo về)
     */
    record Payout(int season, long characterId, int rank, int rating) {}

    /**
     * Bậc thưởng theo hạng — bậc ĐẦU TIÊN (theo {@code rankFrom}) phủ hạng là bậc được trao.
     *
     * @param itemTemplateId 0 nếu chỉ thưởng linh thạch
     */
    record Reward(int rankFrom, int rankTo, long gold, long itemTemplateId, int quantity) {}

    /** Mùa đang mở; rỗng nếu chưa có (dữ liệu hỏng — migration luôn mở mùa 1). */
    Optional<Season> currentSeason();

    /**
     * Chốt mùa {@code number} NẾU nó vẫn đang mở — nguyên tử: khoá dòng mùa → ghi hàng đợi thưởng theo hạng Elo (giảm
     * dần, hoà thì id nhỏ trước) → kéo Elo mọi người về gần mốc ({@link PvpSeasonPolicy#softReset}) → đóng mùa → mở mùa
     * kế dài {@code nextLength}.
     *
     * @return {@code false} nếu mùa đã được chốt (lượt trước đã làm) — không đổi gì
     */
    boolean closeSeason(int number, Instant now, Duration nextLength);

    /** Phần thưởng còn chờ gửi, theo mùa rồi hạng. */
    List<Payout> pendingPayouts();

    /** Thư của {@code payout} đã gửi — xoá khỏi hàng đợi. */
    void markPaid(Payout payout);

    /** Bậc thưởng của mùa (dùng chung mọi mùa ở bản đầu), theo {@code rankFrom}. */
    List<Reward> rewards();
}
