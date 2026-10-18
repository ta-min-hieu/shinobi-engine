package dev.kage.checkin.api;

import java.util.function.LongFunction;

import dev.kage.checkin.application.ClaimDailyCheckInUseCase;

/** API của {@code feature-checkin} (ADR-060). */
public final class CheckInFacade {

    /** Composition không có điểm danh (test) — không bao giờ trao gì. */
    public static final CheckInFacade DISABLED =
            new CheckInFacade(characterId -> new ClaimDailyCheckInUseCase.Result.AlreadyClaimed());

    private final LongFunction<ClaimDailyCheckInUseCase.Result> claim;

    /** Dựng ở composition root. */
    public CheckInFacade(ClaimDailyCheckInUseCase claim) {
        this(claim::execute);
    }

    private CheckInFacade(LongFunction<ClaimDailyCheckInUseCase.Result> claim) {
        this.claim = claim;
    }

    /** Điểm danh hôm nay nếu chưa — gọi mỗi lần vào world, chạm DB (chỉ trên Virtual Thread). */
    public ClaimDailyCheckInUseCase.Result claimToday(long characterId) {
        return claim.apply(characterId);
    }
}
