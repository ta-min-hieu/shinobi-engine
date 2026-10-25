package dev.kage.gm.api;

import java.time.Instant;
import java.util.Optional;

import dev.kage.gm.domain.GiftCode;
import dev.kage.gm.domain.GiftCodeOutcome;
import dev.kage.gm.domain.GiftCodePort;

/** API mã quà (ADR-104). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GiftCodeFacade {

    /** Không có mã nào (test). */
    public static final GiftCodeFacade DISABLED = new GiftCodeFacade(GiftCodePort.NONE);

    private final GiftCodePort port;

    /** Dựng ở composition root. */
    public GiftCodeFacade(GiftCodePort port) {
        this.port = port;
    }

    /** ADMIN tạo mã — {@code false} nếu đã có mã trùng. */
    public boolean create(GiftCode code, long createdBy, Instant at) {
        return port.create(code, createdBy, at);
    }

    /** Người chơi đổi mã; chữ gõ sai định dạng là {@code UNKNOWN_CODE} luôn, không chạm DB. */
    public GiftCodePort.Redemption redeem(String rawCode, long characterId, Instant at) {
        String code = GiftCode.normalize(rawCode);
        if (!GiftCode.isWellFormed(code)) {
            return new GiftCodePort.Redemption(GiftCodeOutcome.UNKNOWN_CODE, Optional.empty(), false);
        }
        return port.redeem(code, characterId, at);
    }

    /** Thư quà đã nằm trong hộp. */
    public void markMailed(String code, long characterId) {
        port.markMailed(code, characterId);
    }
}
