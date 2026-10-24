package dev.kage.character.domain;

import java.time.Instant;

/**
 * Bế quan (ADR-095) — hiện thực ở server-database. {@link #claim} là MỘT transaction: khoá quỹ → (x2) trừ vàng qua
 * GoldLedger → cộng {@code character.exp} → xoá quỹ → audit. Đồng bộ cấp sau đó là việc của gateway (cùng đường thưởng
 * quest).
 */
public interface SeclusionPort {

    /** Kết quả nhận quỹ. */
    enum Outcome {
        OK,
        NOTHING_BANKED,
        INSUFFICIENT_GOLD
    }

    /**
     * Kết quả nhận kèm EXP.
     *
     * @param expGained EXP đã cộng (0 nếu từ chối)
     */
    record Claim(Outcome outcome, long expGained) {}

    /**
     * Vào world: cộng phút offline từ {@code last_logout_at} vào quỹ (kẹp trần) rồi xoá mốc thoát — vào lại mà
     * chưa thoát thì không tích thêm.
     *
     * @return quỹ sau khi cộng
     */
    int bankOfflineTime(long characterId, Instant now);

    /** Rời world: ghi mốc thoát. */
    void markLoggedOut(long characterId, Instant now);

    /** Quỹ hiện tại (0 nếu chưa có dòng). */
    int bankedMinutes(long characterId);

    /** Nhận cả quỹ, x2 nếu {@code doubled}. Cấp đọc trong giao dịch. */
    Claim claim(long characterId, boolean doubled);
}
