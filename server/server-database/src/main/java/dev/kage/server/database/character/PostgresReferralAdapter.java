package dev.kage.server.database.character;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.ReferralPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link ReferralPort} (ADR-320) — bảng {@code referral} (V175); chốt quà bằng UPDATE có điều kiện (một lần).
 */
public final class PostgresReferralAdapter implements ReferralPort {

    private static final String REGISTER =
            "INSERT INTO referral (referred_id, referrer_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
    private static final String CLAIM = """
            UPDATE referral SET rewarded_at = now() WHERE referred_id = ? AND rewarded_at IS NULL RETURNING referrer_id
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresReferralAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean register(long referredId, long referrerId) {
        return JdbcOperations.update(dataSource, REGISTER, referredId, referrerId) == 1;
    }

    @Override
    public Optional<Long> claimReward(long referredId) {
        return JdbcOperations.queryOne(dataSource, CLAIM, rs -> rs.getLong(1), referredId);
    }
}
