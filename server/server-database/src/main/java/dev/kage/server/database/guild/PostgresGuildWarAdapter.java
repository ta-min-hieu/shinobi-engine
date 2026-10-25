package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildWar;
import dev.kage.guild.domain.GuildWarOutcome;
import dev.kage.guild.domain.GuildWarPort;
import dev.kage.guild.domain.GuildWarRules;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GuildWarPort} (ADR-102, V88).
 *
 * <p>Thứ tự khoá khi tuyên: hai dòng {@code guild} theo {@code id} tăng dần (giữ luật "dòng {@code guild} trước" của
 * feature-guild), rồi mới đọc {@code guild_war}. Chốt trận khoá đúng dòng {@code guild_war} chưa chốt — lần gọi thứ hai
 * không thấy dòng nào nên không bao giờ chốt đôi.
 */
public final class PostgresGuildWarAdapter implements GuildWarPort {

    private static final String GOLD_REASON = "gold.reason.guild_war";

    private static final String MY_GUILD = """
            SELECT g.id, g.leader_character_id = ? FROM guild_member gm JOIN guild g ON g.id = gm.guild_id
             WHERE gm.character_id = ?
            """;

    private static final String GUILD_BY_NAME = "SELECT id FROM guild WHERE name = ?";

    private static final String LOCK_GUILDS = "SELECT id FROM guild WHERE id IN (?, ?) ORDER BY id FOR NO KEY UPDATE";

    private static final String BUSY = """
            SELECT 1 FROM guild_war
             WHERE settled_at IS NULL AND (guild_a IN (?, ?) OR guild_b IN (?, ?))
             LIMIT 1
            """;

    private static final String RECENT_PAIR = """
            SELECT 1 FROM guild_war
             WHERE ((guild_a = ? AND guild_b = ?) OR (guild_a = ? AND guild_b = ?)) AND ends_at > ?
             LIMIT 1
            """;

    private static final String INSERT = """
            INSERT INTO guild_war (guild_a, guild_b, declared_by, declared_at, starts_at, ends_at)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private static final String WAR_COLUMNS = """
            SELECT w.id, w.guild_a, ga.name, w.kills_a, w.guild_b, gb.name, w.kills_b, w.starts_at, w.ends_at
              FROM guild_war w JOIN guild ga ON ga.id = w.guild_a JOIN guild gb ON gb.id = w.guild_b
            """;

    private static final String BY_ID = WAR_COLUMNS + " WHERE w.id = ?";

    private static final String UNSETTLED = WAR_COLUMNS + " WHERE w.settled_at IS NULL ORDER BY w.starts_at, w.id";

    private static final String COMBATANTS = """
            SELECT gm.character_id, gm.guild_id FROM guild_member gm
             WHERE gm.guild_id IN (
                   SELECT guild_a FROM guild_war WHERE settled_at IS NULL AND starts_at <= ? AND ends_at > ?
                   UNION
                   SELECT guild_b FROM guild_war WHERE settled_at IS NULL AND starts_at <= ? AND ends_at > ?)
            """;

    private static final String RECORD_KILL = """
            UPDATE guild_war w
               SET kills_a = w.kills_a + CASE WHEN w.guild_a = k.guild_id THEN 1 ELSE 0 END,
                   kills_b = w.kills_b + CASE WHEN w.guild_b = k.guild_id THEN 1 ELSE 0 END
              FROM guild_member k, guild_member v
             WHERE k.character_id = ? AND v.character_id = ?
               AND w.settled_at IS NULL AND w.starts_at <= ? AND w.ends_at > ?
               AND ((w.guild_a = k.guild_id AND w.guild_b = v.guild_id)
                 OR (w.guild_b = k.guild_id AND w.guild_a = v.guild_id))
            """;

    private static final String LOCK_DUE =
            "SELECT id FROM guild_war WHERE id = ? AND settled_at IS NULL AND ends_at <= ? FOR UPDATE";

    private static final String MARK_SETTLED = "UPDATE guild_war SET settled_at = ?, winner_guild_id = ? WHERE id = ?";

    private static final String QUEUE_PAYOUTS = """
            INSERT INTO guild_war_payout (war_id, character_id, guild_name)
            SELECT ?, gm.character_id, ? FROM guild_member gm WHERE gm.guild_id = ?
            ON CONFLICT DO NOTHING
            """;

    private static final String PENDING =
            "SELECT war_id, character_id, guild_name FROM guild_war_payout ORDER BY war_id, character_id";

    private static final String MARK_PAID = "DELETE FROM guild_war_payout WHERE war_id = ? AND character_id = ?";

    private final DataSource dataSource;

    public PostgresGuildWarAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Bang của người tuyên. */
    private record Membership(long guildId, boolean leader) {}

    @Override
    public Declared declare(Declaration declaration) {
        return JdbcTransactionRunner.call(dataSource, connection -> declareIn(connection, declaration));
    }

    private static Declared declareIn(Connection connection, Declaration declaration) {
        Optional<Membership> mine = membership(connection, declaration.leaderId());
        if (mine.isEmpty()) {
            return refused(GuildWarOutcome.NOT_IN_GUILD);
        }
        if (!mine.get().leader()) {
            return refused(GuildWarOutcome.NOT_LEADER);
        }
        long attacker = mine.get().guildId();
        Optional<Long> target =
                JdbcOperations.queryOne(connection, GUILD_BY_NAME, rs -> rs.getLong(1), declaration.targetGuildName());
        if (target.isEmpty()) {
            return refused(GuildWarOutcome.UNKNOWN_GUILD);
        }
        long defender = target.get();
        if (defender == attacker) {
            return refused(GuildWarOutcome.SAME_GUILD);
        }
        JdbcOperations.lock(connection, LOCK_GUILDS, attacker, defender);
        Optional<GuildWarOutcome> blocked = blockedBy(connection, attacker, defender, declaration.at());
        if (blocked.isPresent()) {
            return refused(blocked.get());
        }
        if (!GoldLedger.deduct(connection, declaration.leaderId(), GuildWarRules.DECLARE_COST, GOLD_REASON)) {
            return refused(GuildWarOutcome.INSUFFICIENT_GOLD);
        }
        Instant startsAt = declaration.at().plus(GuildWarRules.PREPARATION);
        long warId = JdbcOperations.insertReturningId(
                connection,
                INSERT,
                attacker,
                defender,
                declaration.leaderId(),
                Timestamp.from(declaration.at()),
                Timestamp.from(startsAt),
                Timestamp.from(startsAt.plus(GuildWarRules.DURATION)));
        JdbcOperations.update(
                connection,
                AUDIT,
                declaration.leaderId(),
                "GUILD_WAR_DECLARED",
                "{\"warId\":" + warId + ",\"targetGuildId\":" + defender + ",\"goldCost\":" + GuildWarRules.DECLARE_COST
                        + "}",
                Timestamp.from(declaration.at()));
        return new Declared(GuildWarOutcome.OK, war(connection, warId));
    }

    private static Optional<Membership> membership(Connection connection, long characterId) {
        return JdbcOperations.queryOne(
                connection, MY_GUILD, rs -> new Membership(rs.getLong(1), rs.getBoolean(2)), characterId, characterId);
    }

    private static Optional<GuildWarOutcome> blockedBy(Connection connection, long first, long second, Instant at) {
        if (JdbcOperations.queryOne(connection, BUSY, rs -> rs.getInt(1), first, second, first, second)
                .isPresent()) {
            return Optional.of(GuildWarOutcome.ALREADY_AT_WAR);
        }
        Timestamp since = Timestamp.from(at.minus(GuildWarRules.REMATCH_COOLDOWN));
        if (JdbcOperations.queryOne(connection, RECENT_PAIR, rs -> rs.getInt(1), first, second, second, first, since)
                .isPresent()) {
            return Optional.of(GuildWarOutcome.COOLDOWN);
        }
        return Optional.empty();
    }

    private static Declared refused(GuildWarOutcome outcome) {
        return new Declared(outcome, Optional.empty());
    }

    @Override
    public List<GuildWar> unsettled() {
        return JdbcOperations.query(dataSource, UNSETTLED, PostgresGuildWarAdapter::mapWar);
    }

    @Override
    public List<Combatant> combatants(Instant now) {
        Timestamp at = Timestamp.from(now);
        return JdbcOperations.query(
                dataSource, COMBATANTS, rs -> new Combatant(rs.getLong(1), rs.getLong(2)), at, at, at, at);
    }

    @Override
    public boolean recordKill(long killerId, long victimId, Instant at) {
        Timestamp when = Timestamp.from(at);
        return JdbcOperations.update(dataSource, RECORD_KILL, killerId, victimId, when, when) > 0;
    }

    @Override
    public Optional<GuildWar> settle(long warId, Instant now) {
        return JdbcTransactionRunner.call(dataSource, connection -> settleIn(connection, warId, now));
    }

    private static Optional<GuildWar> settleIn(Connection connection, long warId, Instant now) {
        if (JdbcOperations.queryOne(connection, LOCK_DUE, rs -> rs.getLong(1), warId, Timestamp.from(now))
                .isEmpty()) {
            return Optional.empty();
        }
        Optional<GuildWar> war = war(connection, warId);
        if (war.isEmpty()) {
            return Optional.empty();
        }
        Optional<GuildWar.Side> winner = GuildWarRules.winner(war.get());
        JdbcOperations.update(
                connection,
                MARK_SETTLED,
                Timestamp.from(now),
                winner.map(GuildWar.Side::guildId).orElse(null),
                warId);
        winner.ifPresent(
                side -> JdbcOperations.update(connection, QUEUE_PAYOUTS, warId, side.guildName(), side.guildId()));
        return war;
    }

    @Override
    public List<Payout> pendingPayouts() {
        return JdbcOperations.query(
                dataSource, PENDING, rs -> new Payout(rs.getLong(1), rs.getLong(2), rs.getString(3)));
    }

    @Override
    public void markPaid(Payout payout) {
        JdbcOperations.update(dataSource, MARK_PAID, payout.warId(), payout.characterId());
    }

    private static Optional<GuildWar> war(Connection connection, long warId) {
        return JdbcOperations.queryOne(connection, BY_ID, PostgresGuildWarAdapter::mapWar, warId);
    }

    private static GuildWar mapWar(ResultSet rs) throws SQLException {
        return new GuildWar(
                rs.getLong(1),
                new GuildWar.Side(rs.getLong(2), rs.getString(3), rs.getInt(4)),
                new GuildWar.Side(rs.getLong(5), rs.getString(6), rs.getInt(7)),
                rs.getTimestamp(8).toInstant(),
                rs.getTimestamp(9).toInstant());
    }
}
