package dev.kage.character.domain;

import java.util.Optional;

/** Mã giới thiệu (ADR-320) — bảng {@code referral}. */
public interface ReferralPort {

    /**
     * Ghi {@code referredId} được {@code referrerId} giới thiệu; {@code false} nếu người này đã được giới thiệu rồi.
     */
    boolean register(long referredId, long referrerId);

    /** Chốt quà MỘT lần: trả người giới thiệu nếu chưa chốt, rỗng nếu không có giới thiệu hoặc đã chốt. */
    Optional<Long> claimReward(long referredId);
}
