package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.SpiritVein;
import dev.kage.guild.domain.SpiritVeinPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link SpiritVeinPort} (ADR-090, V84). Chốt khung khoá dòng {@code spirit_vein} rồi chèn dòng
 * {@code spirit_vein_settlement} — khoá chính của bảng đó làm phép chốt chạy đúng một lần.
 */
public final class PostgresSpiritVeinAdapter implements SpiritVeinPort {

    private static final String VEINS = """
            SELECT code, name_key, min_x, max_x, weekday, start_minute, duration_minutes,
                   reward_gold, COALESCE(reward_item_template_id, 0), reward_quantity
              FROM spirit_vein ORDER BY code
            """;

    private static final String HOLDER = """
            SELECT g.id, g.name, v.held_until FROM spirit_vein v JOIN guild g ON g.id = v.holder_guild_id
             WHERE v.code = ? AND v.held_until > ?
            """;

    private static final String ADD_POINT = """
            INSERT INTO spirit_vein_score (vein_code, window_start, guild_id, points, reached_at) VALUES (?, ?, ?, 1, ?)
            ON CONFLICT (vein_code, window_start, guild_id)
            DO UPDATE SET points = spirit_vein_score.points + 1, reached_at = EXCLUDED.reached_at
            """;

    private static final String SCORES = """
            SELECT s.guild_id, g.name, s.points FROM spirit_vein_score s JOIN guild g ON g.id = s.guild_id
             WHERE s.vein_code = ? AND s.window_start = ?
             ORDER BY s.points DESC, s.reached_at, s.guild_id
            """;

    private static final String LOCK_VEIN = "SELECT code FROM spirit_vein WHERE code = ? FOR UPDATE";

    private static final String RECORD_SETTLEMENT = """
            INSERT INTO spirit_vein_settlement (vein_code, window_start, winner_guild_id, settled_at)
            VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String SET_HOLDER =
            "UPDATE spirit_vein SET holder_guild_id = ?, held_until = ? WHERE code = ?";

    private static final String QUEUE_PAYOUTS = """
            INSERT INTO spirit_vein_payout (vein_code, window_start, character_id, guild_name)
            SELECT ?, ?, gm.character_id, ? FROM guild_member gm WHERE gm.guild_id = ?
            ON CONFLICT DO NOTHING
            """;

    private static final String PENDING = """
            SELECT vein_code, window_start, character_id, guild_name FROM spirit_vein_payout
             ORDER BY window_start, vein_code, character_id
            """;

    private static final String MARK_PAID =
            "DELETE FROM spirit_vein_payout WHERE vein_code = ? AND window_start = ? AND character_id = ?";

    private final DataSource dataSource;

    public PostgresSpiritVeinAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<SpiritVein> veins() {
        return JdbcOperations.query(dataSource, VEINS, PostgresSpiritVeinAdapter::vein);
    }

    @Override
    public Optional<Holder> holderOf(String veinCode, Instant now) {
        return JdbcOperations.queryOne(
                dataSource,
                HOLDER,
                rs -> new Holder(
                        rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
                veinCode,
                Timestamp.from(now));
    }

    @Override
    public void addPoint(String veinCode, Instant windowStart, long guildId, Instant at) {
        JdbcOperations.update(
                dataSource, ADD_POINT, veinCode, Timestamp.from(windowStart), guildId, Timestamp.from(at));
    }

    @Override
    public List<Score> scores(String veinCode, Instant windowStart) {
        return JdbcOperations.query(
                dataSource,
                SCORES,
                rs -> new Score(rs.getLong(1), rs.getString(2), rs.getInt(3)),
                veinCode,
                Timestamp.from(windowStart));
    }

    @Override
    public Optional<Score> settle(String veinCode, Instant windowStart, Instant heldUntil) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> settleIn(connection, veinCode, windowStart, heldUntil));
    }

    private static Optional<Score> settleIn(
            Connection connection, String veinCode, Instant windowStart, Instant heldUntil) {
        JdbcOperations.lock(connection, LOCK_VEIN, veinCode);
        Timestamp start = Timestamp.from(windowStart);
        Optional<Score> winner = JdbcOperations.query(
                        connection,
                        SCORES,
                        rs -> new Score(rs.getLong(1), rs.getString(2), rs.getInt(3)),
                        veinCode,
                        start)
                .stream()
                .findFirst();
        Long winnerId = winner.map(Score::guildId).orElse(null);
        int inserted = JdbcOperations.update(
                connection, RECORD_SETTLEMENT, veinCode, start, winnerId, Timestamp.from(Instant.now()));
        if (inserted == 0 || winner.isEmpty()) {
            return Optional.empty();
        }
        JdbcOperations.update(connection, SET_HOLDER, winnerId, Timestamp.from(heldUntil), veinCode);
        JdbcOperations.update(
                connection, QUEUE_PAYOUTS, veinCode, start, winner.get().guildName(), winnerId);
        return winner;
    }

    @Override
    public List<Payout> pendingPayouts() {
        return JdbcOperations.query(
                dataSource,
                PENDING,
                rs -> new Payout(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getLong(3), rs.getString(4)));
    }

    @Override
    public void markPaid(Payout payout) {
        JdbcOperations.update(
                dataSource, MARK_PAID, payout.veinCode(), Timestamp.from(payout.windowStart()), payout.characterId());
    }

    private static SpiritVein vein(ResultSet rs) throws SQLException {
        return new SpiritVein(
                rs.getString(1),
                rs.getString(2),
                new SpiritVein.Zone(rs.getFloat(3), rs.getFloat(4)),
                new SpiritVein.Schedule(rs.getInt(5), rs.getInt(6), rs.getInt(7)),
                new SpiritVein.Reward(rs.getLong(8), rs.getLong(9), rs.getInt(10)));
    }
}
