package dev.kage.character.api;

import java.util.Optional;

import dev.kage.character.domain.ReferralPort;

/** API mã giới thiệu (ADR-320): nhập trước cấp {@value #REWARD_LEVEL}; tới cấp đó cả hai nhận quà một lần. */
public final class ReferralFacade {

    /** Cấp người mới phải đạt để cả hai nhận quà; từ cấp này trở lên không nhập mã được nữa. */
    public static final int REWARD_LEVEL = 10;

    /** Không lưu gì (composition test). */
    public static final ReferralFacade DISABLED = new ReferralFacade(new ReferralPort() {
        @Override
        public boolean register(long referredId, long referrerId) {
            return false;
        }

        @Override
        public Optional<Long> claimReward(long referredId) {
            return Optional.empty();
        }
    });

    private final ReferralPort port;

    /** Dựng ở composition root. */
    public ReferralFacade(ReferralPort port) {
        this.port = port;
    }

    /** Ghi giới thiệu (người gọi đã kiểm cấp, tự giới thiệu, cùng tài khoản). */
    public boolean register(long referredId, long referrerId) {
        return port.register(referredId, referrerId);
    }

    /** Chốt quà một lần. */
    public Optional<Long> claimReward(long referredId) {
        return port.claimReward(referredId);
    }
}
