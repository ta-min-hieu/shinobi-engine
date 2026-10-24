package dev.kage.server.database.pvp;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpSeasonPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link PvpSeasonPort} (ADR-087, V82). Chốt mùa khoá dòng {@code pvp_season} TRƯỚC rồi mới đụng
 * {@code pvp_rating} — hai lượt chốt song song xếp hàng ở dòng mùa, lượt sau thấy {@code closed_at} đã có và không làm
 * gì.
 */
public final class PostgresPvpSeasonAdapter implements PvpSeasonPort {

    private static final String CURRENT = """
            SELECT number, ends_at FROM pvp_season WHERE closed_at IS NULL ORDER BY number DESC LIMIT 1
            """;

    private static final String LOCK_OPEN =
            "SELECT number FROM pvp_season WHERE number = ? AND closed_at IS NULL FOR UPDATE";

    // Khoá mọi dòng Elo theo character_id TĂNG DẦN trước khi sửa — cùng thứ tự với chốt trận đấu
    // (PostgresPvpRatingAdapter), nên một trận settle đúng lúc chốt mùa xếp hàng thay vì deadlock.
    private static final String LOCK_RATINGS = "SELECT character_id FROM pvp_rating ORDER BY character_id FOR UPDATE";

    private static final String QUEUE_PAYOUTS = """
            INSERT INTO pvp_season_payout (season, character_id, rank, rating)
            SELECT ?, character_id, ROW_NUMBER() OVER (ORDER BY rating DESC, character_id), rating
              FROM pvp_rating
             WHERE wins + losses > 0
            """;

    private static final String PENDING =
            "SELECT season, character_id, rank, rating FROM pvp_season_payout ORDER BY season, rank";

    private static final String MARK_PAID = "DELETE FROM pvp_season_payout WHERE season = ? AND character_id = ?";

    // Kéo nửa đường về mốc — cùng công thức PvpSeasonPolicy.softReset, làm trong SQL để một câu đổi cả bảng. Chia số
    // nguyên của Postgres cắt về 0 như Java, nên hai bên ra cùng số kể cả Elo dưới mốc.
    private static final String SOFT_RESET = "UPDATE pvp_rating SET rating = ? + (rating - ?) / 2, updated_at = ?";

    private static final String CLOSE = "UPDATE pvp_season SET closed_at = ? WHERE number = ?";

    private static final String OPEN_NEXT = "INSERT INTO pvp_season (number, started_at, ends_at) VALUES (?, ?, ?)";

    private static final String REWARDS = """
            SELECT rank_from, rank_to, gold, COALESCE(item_template_id, 0), quantity
              FROM pvp_season_reward ORDER BY rank_from
            """;

    private final DataSource dataSource;

    public PostgresPvpSeasonAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Season> currentSeason() {
        return JdbcOperations.queryOne(
                dataSource,
                CURRENT,
                rs -> new Season(rs.getInt(1), rs.getTimestamp(2).toInstant()));
    }

    @Override
    public boolean closeSeason(int number, Instant now, Duration nextLength) {
        return JdbcTransactionRunner.call(dataSource, connection -> closeIn(connection, number, now, nextLength));
    }

    private static boolean closeIn(Connection connection, int number, Instant now, Duration next) {
        if (JdbcOperations.queryOne(connection, LOCK_OPEN, rs -> rs.getInt(1), number)
                .isEmpty()) {
            return false;
        }
        JdbcOperations.query(connection, LOCK_RATINGS, rs -> rs.getLong(1));
        JdbcOperations.update(connection, QUEUE_PAYOUTS, number);
        Timestamp at = Timestamp.from(now);
        JdbcOperations.update(connection, SOFT_RESET, EloRating.DEFAULT_RATING, EloRating.DEFAULT_RATING, at);
        JdbcOperations.update(connection, CLOSE, at, number);
        JdbcOperations.update(connection, OPEN_NEXT, number + 1, at, Timestamp.from(now.plus(next)));
        return true;
    }

    @Override
    public List<Payout> pendingPayouts() {
        return JdbcOperations.query(
                dataSource, PENDING, rs -> new Payout(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)));
    }

    @Override
    public void markPaid(Payout payout) {
        JdbcOperations.update(dataSource, MARK_PAID, payout.season(), payout.characterId());
    }

    @Override
    public List<Reward> rewards() {
        return JdbcOperations.query(
                dataSource,
                REWARDS,
                rs -> new Reward(rs.getInt(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getInt(5)));
    }
}
