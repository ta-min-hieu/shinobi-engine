package dev.kage.pvp.application;

import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRatingPort;

/** Chốt kết quả 1 trận Arena — tính Elo mới rồi ghi nguyên tử qua {@link PvpRatingPort}. */
public final class RecordDuelResultUseCase {

    private final PvpRatingPort port;

    public RecordDuelResultUseCase(PvpRatingPort port) {
        this.port = port;
    }

    /**
     * Chốt trận: tính và ghi rating mới của người thắng và người thua.
     *
     * <p>Đọc-tính-ghi rating giao HẲN cho {@link PvpRatingPort#recordDuelResult} làm NGUYÊN TỬ
     * (khoá row trước khi đọc) — trước đây use case này tự đọc rời 2 rating rồi mới gọi Port ghi,
     * để lộ khoảng trống giữa đọc và ghi mà 2 trận của CÙNG 1 nhân vật chốt gần như đồng thời lọt
     * qua được (xem javadoc {@link PvpRatingPort#recordDuelResult}). {@code EloRating::computeNewRatings}
     * vẫn là hàm THUẦN duy nhất chứa công thức Elo — chỉ đổi chỗ nó được gọi (trong transaction của
     * Port) chứ không đổi công thức.
     *
     * @return rating MỚI của cả 2 sau trận — gateway dùng để đóng gói {@code DuelResultEvent}.
     */
    public DuelSettlement execute(long winnerCharacterId, long loserCharacterId) {
        EloRating.Result result =
                port.recordDuelResult(winnerCharacterId, loserCharacterId, EloRating::computeNewRatings);
        return new DuelSettlement(
                winnerCharacterId, result.newWinnerRating(), loserCharacterId, result.newLoserRating());
    }

    /** Gộp tham số kết quả trả về (STATIC-ANALYSIS.md). */
    public record DuelSettlement(
            long winnerCharacterId, int winnerNewRating, long loserCharacterId, int loserNewRating) {}
}
