package dev.kage.pvp.api;

import java.util.List;

import dev.kage.pvp.application.GetPvpRatingUseCase;
import dev.kage.pvp.application.RecordDuelResultUseCase;
import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;

/**
 * API công khai của {@code feature-pvp} — module khác ({@code server-gateway}, qua
 * {@code ArenaInstanceRegistry}) CHỈ gọi qua đây, không bao giờ đọc bảng {@code pvp_rating} trực
 * tiếp (Điều 24). Chỉ 2 UseCase (nhỏ hơn hẳn GuildFacade/PartyFacade) nên KHÔNG cần bundle
 * {@code PvpUseCases} riêng — constructor 1 tham số ({@link PvpRatingPort}) đã dưới giới hạn 4.
 */
public final class PvpFacade {

    private final RecordDuelResultUseCase recordDuelResultUseCase;
    private final GetPvpRatingUseCase getPvpRatingUseCase;
    private final PvpRatingPort port;

    /** Dựng ở composition root. */
    public PvpFacade(PvpRatingPort port) {
        this.recordDuelResultUseCase = new RecordDuelResultUseCase(port);
        this.getPvpRatingUseCase = new GetPvpRatingUseCase(port);
        this.port = port;
    }

    /** Mọi người đã đánh ít nhất một trận, Elo giảm dần — cho Bia Anh Danh (ADR-057); gateway cache 60 s. */
    public List<PvpRating> listAllByRating() {
        return port.listAllByRating();
    }

    /** Chốt kết quả 1 trận Arena vừa xong (hạ gục hoặc bỏ cuộc) — trả rating mới của cả 2. */
    public RecordDuelResultUseCase.DuelSettlement recordDuelResult(long winnerCharacterId, long loserCharacterId) {
        return recordDuelResultUseCase.execute(winnerCharacterId, loserCharacterId);
    }

    /** Elo + số trận thắng/thua — người chưa đấu trận nào nhận rating mặc định, 0 thắng 0 thua. */
    public GetPvpRatingUseCase.RatingView findRating(long characterId) {
        return getPvpRatingUseCase.execute(characterId);
    }
}
