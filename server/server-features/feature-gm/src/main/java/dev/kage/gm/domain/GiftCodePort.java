package dev.kage.gm.domain;

import java.time.Instant;
import java.util.Optional;

/** Mã quà (ADR-104) — hiện thực ở server-database (V89). */
public interface GiftCodePort {

    /**
     * Kết quả một lượt đổi.
     *
     * @param code mã như lúc đổi — có mặt khi {@code outcome == OK}
     * @param needsMail thư quà CHƯA chắc đã nằm trong hộp (lượt đổi mới, hoặc lượt cũ mà thư chưa gửi được)
     */
    record Redemption(GiftCodeOutcome outcome, Optional<GiftCode> code, boolean needsMail) {}

    /** Không có mã nào (test). */
    GiftCodePort NONE = new GiftCodePort() {
        @Override
        public boolean create(GiftCode code, long createdBy, Instant at) {
            return false;
        }

        @Override
        public Redemption redeem(String code, long characterId, Instant at) {
            return new Redemption(GiftCodeOutcome.UNKNOWN_CODE, Optional.empty(), false);
        }

        @Override
        public void markMailed(String code, long characterId) {
            // Không có lượt đổi nào.
        }
    };

    /**
     * Tạo mã mới.
     *
     * @return {@code false} nếu mã đã tồn tại
     */
    boolean create(GiftCode code, long createdBy, Instant at);

    /**
     * Đổi mã — nguyên tử: khoá dòng mã, kiểm hạn/lượt, chèn dòng đổi, tăng lượt. Đã đổi mà thư chưa gửi được ⇒
     * {@code OK} + {@code needsMail} (gửi lại, KHÔNG tăng lượt).
     */
    Redemption redeem(String code, long characterId, Instant at);

    /** Thư quà của lượt đổi này đã nằm trong hộp. */
    void markMailed(String code, long characterId);
}
