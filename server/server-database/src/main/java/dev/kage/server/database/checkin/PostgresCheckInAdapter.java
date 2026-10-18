package dev.kage.server.database.checkin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.checkin.domain.CheckInPort;
import dev.kage.checkin.domain.CheckInReward;
import dev.kage.checkin.domain.CheckInState;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link CheckInPort} (ADR-060, V62). Ghi chỉ bằng compare-and-set. */
public final class PostgresCheckInAdapter implements CheckInPort {

    private static final String FIND = "SELECT last_day, last_claim_date FROM daily_check_in WHERE character_id = ?";

    /** Lần đầu: chỉ chèn khi chưa có dòng — hai lượt cùng chèn thì một lượt nhận 0 dòng. */
    private static final String INSERT_FIRST = """
            INSERT INTO daily_check_in (character_id, last_day, last_claim_date, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (character_id) DO NOTHING
            """;

    private static final String ADVANCE = """
            UPDATE daily_check_in SET last_day = ?, last_claim_date = ?, updated_at = ?
            WHERE character_id = ? AND last_day = ? AND last_claim_date = ?
            """;

    private static final String REWARDS = """
            SELECT day, gold_amount, COALESCE(item_code, '') AS item_code, quantity
            FROM daily_check_in_reward ORDER BY day
            """;

    private final DataSource dataSource;

    public PostgresCheckInAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<CheckInState> find(long characterId) {
        return JdbcOperations.queryOne(
                dataSource,
                FIND,
                rs -> new CheckInState(rs.getInt("last_day"), rs.getObject("last_claim_date", LocalDate.class)),
                characterId);
    }

    @Override
    public boolean advance(long characterId, CheckInState expected, CheckInState next) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (expected.isNever()) {
            return JdbcOperations.update(
                            dataSource, INSERT_FIRST, characterId, next.lastDay(), next.lastClaimDate(), now)
                    == 1;
        }
        return JdbcOperations.update(
                        dataSource,
                        ADVANCE,
                        next.lastDay(),
                        next.lastClaimDate(),
                        now,
                        characterId,
                        expected.lastDay(),
                        expected.lastClaimDate())
                == 1;
    }

    @Override
    public List<CheckInReward> rewards() {
        return JdbcOperations.query(
                dataSource,
                REWARDS,
                rs -> new CheckInReward(
                        rs.getInt("day"), rs.getLong("gold_amount"), rs.getString("item_code"), rs.getInt("quantity")));
    }
}
