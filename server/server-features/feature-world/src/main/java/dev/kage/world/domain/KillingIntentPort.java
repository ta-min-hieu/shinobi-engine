package dev.kage.world.domain;

import java.time.Instant;
import java.util.Set;

/** Sát khí (ADR-142, bảng {@code character_killing_intent}, V115) — adapter thật ở {@code server-database}. */
public interface KillingIntentPort {

    /** Sát khí hiện tại (đã trừ phân rã; 0 nếu chưa từng có). */
    int points(long characterId, Instant now);

    /**
     * {@code killerId} vừa hạ một người trong dải PvP: khoá dòng, cộng một điểm theo
     * {@link KillingIntentRules#afterKill}, mốc phân rã mới = {@code now}.
     *
     * @return sát khí sau khi cộng
     */
    int recordKill(long killerId, Instant now);

    /** Mọi nhân vật đang là Ma Đầu lúc {@code now}. */
    Set<Long> outlaws(Instant now);

    /**
     * Ma Đầu {@code victimId} vừa chết: một transaction trừ {@link KillingIntentRules#goldDrop} vàng của họ và, nếu
     * {@code killerId > 0}, cộng đúng số đó cho người hạ; ghi audit. Không phải Ma Đầu (kiểm lại TRONG transaction)
     * thì không làm gì.
     *
     * @return số vàng đã rơi (0 nếu không rơi gì)
     */
    long dropGold(long victimId, long killerId, Instant now);
}
