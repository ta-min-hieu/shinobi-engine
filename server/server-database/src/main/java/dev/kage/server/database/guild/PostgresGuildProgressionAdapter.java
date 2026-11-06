package dev.kage.server.database.guild;

import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import dev.kage.guild.domain.DonationOutcome;
import dev.kage.guild.domain.GuildLevelTable;
import dev.kage.guild.domain.GuildProgressionPort;
import dev.kage.guild.domain.GuildRank;
import dev.kage.guild.domain.GuildStanding;
import dev.kage.guild.domain.GuildWeeklyQuest;
import dev.kage.guild.domain.WeeklyCompletion;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GuildProgressionPort} (ADR-068/069, V70). Thứ tự khoá trong mọi giao dịch ghi: dòng
 * {@code guild} ({@code FOR UPDATE}) TRƯỚC, rồi vàng — không đường nào khác khoá {@code guild} sau vàng.
 */
public final class PostgresGuildProgressionAdapter implements GuildProgressionPort {

    private static final String GOLD_REASON = "gold.reason.guild_donation";

    private static final String STANDING = """
            SELECT g.id, g.level, g.exp, gm.contribution,
                   COALESCE(w.kills, 0) AS kills, (w.completed_at IS NOT NULL) AS completed
              FROM guild_member gm
              JOIN guild g ON g.id = gm.guild_id
              LEFT JOIN guild_weekly_progress w ON w.guild_id = g.id AND w.week_start = ?
             WHERE gm.character_id = ?
            """;

    private static final String GUILD_OF = "SELECT guild_id FROM guild_member WHERE character_id = ?";
    private static final String LOCK_GUILD_EXP = "SELECT exp, level FROM guild WHERE id = ? FOR UPDATE";
    private static final String SET_GUILD_EXP = "UPDATE guild SET exp = ?, level = ? WHERE id = ?";
    private static final String ADD_CONTRIBUTION =
            "UPDATE guild_member SET contribution = contribution + ? WHERE character_id = ?";
    private static final String INSERT_IDEMPOTENCY_KEY = "INSERT INTO idempotency_key (key, created_at) VALUES (?, ?)";
    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Chỉ cộng khi tuần CHƯA xong — tuần đã chốt thì không trả dòng nào. */
    private static final String COUNT_KILL = """
            INSERT INTO guild_weekly_progress (guild_id, week_start, kills) VALUES (?, ?, 1)
            ON CONFLICT (guild_id, week_start) DO UPDATE
               SET kills = guild_weekly_progress.kills + 1
             WHERE guild_weekly_progress.completed_at IS NULL
            RETURNING kills
            """;

    private static final String COMPLETE_WEEK = """
            UPDATE guild_weekly_progress SET completed_at = ?
             WHERE guild_id = ? AND week_start = ? AND completed_at IS NULL
            """;

    private static final String MEMBERS =
            "SELECT character_id FROM guild_member WHERE guild_id = ? ORDER BY character_id";

    private static final String CONTRIBUTIONS =
            "SELECT character_id, contribution FROM guild_member WHERE guild_id = ?";

    private static final String RANK = """
            SELECT g.id, g.name, g.leader_character_id, g.level, g.exp,
                   ARRAY_AGG(gm.character_id ORDER BY gm.character_id) AS members
              FROM guild g JOIN guild_member gm ON gm.guild_id = g.id
             GROUP BY g.id
             ORDER BY g.exp DESC, g.id
            """;

    private final DataSource dataSource;

    public PostgresGuildProgressionAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<GuildStanding> standingOf(long characterId, LocalDate weekStart) {
        return JdbcOperations.queryOne(
                dataSource,
                STANDING,
                rs -> new GuildStanding(
                        rs.getLong("id"),
                        rs.getInt("level"),
                        rs.getLong("exp"),
                        rs.getLong("contribution"),
                        rs.getInt("kills"),
                        rs.getBoolean("completed")),
                Date.valueOf(weekStart),
                characterId);
    }

    /** Lý do huỷ giao dịch mà vẫn là một kết quả nghiệp vụ bình thường (rollback mọi thứ đã ghi). */
    private static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient DonationOutcome outcome;

        Refused(DonationOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }

    @Override
    public Donation donate(long characterId, long amount, String idempotencyKey) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> donateIn(connection, characterId, amount, idempotencyKey));
        } catch (Refused refused) {
            return new Donation(refused.outcome, false, 0L);
        }
    }

    private static Donation donateIn(Connection connection, long characterId, long amount, String idempotencyKey) {
        Optional<Long> guildId = JdbcOperations.queryOne(connection, GUILD_OF, rs -> rs.getLong(1), characterId);
        if (guildId.isEmpty()) {
            return new Donation(DonationOutcome.NOT_IN_GUILD, false, 0L);
        }
        long id = guildId.get();
        int levelBefore = lockGuildLevel(connection, id);
        JdbcOperations.updateOrThrow(
                connection,
                INSERT_IDEMPOTENCY_KEY,
                () -> new Refused(DonationOutcome.DUPLICATE_REQUEST),
                UUID.fromString(idempotencyKey),
                Timestamp.from(Instant.now()));
        if (!GoldLedger.deduct(connection, characterId, amount, GOLD_REASON)) {
            throw new Refused(DonationOutcome.INSUFFICIENT_GOLD);
        }
        int levelAfter = addGuildExp(connection, id, amount);
        JdbcOperations.update(connection, ADD_CONTRIBUTION, amount, characterId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "GUILD_DONATION",
                "{\"guildId\":" + id + ",\"amount\":" + amount + "}",
                Timestamp.from(Instant.now()));
        logDonation(
                connection,
                new GuildLog.Entry(id, GuildLog.Kind.DONATED, characterId, 0L, amount, ""),
                levelAfter > levelBefore ? levelAfter : 0);
        return new Donation(DonationOutcome.OK, levelAfter > levelBefore, id);
    }

    /** ADR-180 — dòng góp bang, và dòng "bang lên cấp" nếu lần góp này đẩy cấp lên ({@code newLevel} > 0). */
    private static void logDonation(Connection connection, GuildLog.Entry donated, int newLevel) {
        Instant now = Instant.now();
        GuildLog.append(connection, donated, now);
        if (newLevel > 0) {
            GuildLog.append(
                    connection,
                    new GuildLog.Entry(donated.guildId(), GuildLog.Kind.LEVEL_UP, donated.actorId(), 0L, newLevel, ""),
                    now);
        }
    }

    @Override
    public Optional<WeeklyCompletion> recordKill(long characterId, LocalDate weekStart) {
        return JdbcTransactionRunner.call(dataSource, connection -> recordKillIn(connection, characterId, weekStart));
    }

    private static Optional<WeeklyCompletion> recordKillIn(
            Connection connection, long characterId, LocalDate weekStart) {
        Optional<Long> guildId = JdbcOperations.queryOne(connection, GUILD_OF, rs -> rs.getLong(1), characterId);
        if (guildId.isEmpty()) {
            return Optional.empty();
        }
        long id = guildId.get();
        Date week = Date.valueOf(weekStart);
        Optional<Integer> kills = JdbcOperations.queryOne(connection, COUNT_KILL, rs -> rs.getInt(1), id, week);
        if (kills.isEmpty() || kills.get() < GuildWeeklyQuest.TARGET_KILLS) {
            return Optional.empty();
        }
        // Mốc chỉ CHẠM một lần: dòng đã chốt thì COUNT_KILL không còn trả gì, và chỉ một giao dịch
        // chốt được (completed_at IS NULL dưới khoá dòng của chính câu INSERT ... ON CONFLICT).
        if (JdbcOperations.update(connection, COMPLETE_WEEK, Timestamp.from(Instant.now()), id, week) != 1) {
            return Optional.empty();
        }
        int levelBefore = lockGuildLevel(connection, id);
        int levelAfter = addGuildExp(connection, id, GuildWeeklyQuest.REWARD_GUILD_EXP);
        List<Long> members = JdbcOperations.query(connection, MEMBERS, rs -> rs.getLong(1), id);
        return Optional.of(new WeeklyCompletion(id, members, levelAfter > levelBefore));
    }

    @Override
    public Map<Long, Long> contributionsOf(long guildId) {
        Map<Long, Long> result = new HashMap<>();
        JdbcOperations.query(dataSource, CONTRIBUTIONS, rs -> Map.entry(rs.getLong(1), rs.getLong(2)), guildId)
                .forEach(row -> result.put(row.getKey(), row.getValue()));
        return result;
    }

    @Override
    public List<GuildRank> rankGuilds() {
        return JdbcOperations.query(
                dataSource,
                RANK,
                rs -> new GuildRank(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("leader_character_id"),
                        rs.getInt("level"),
                        rs.getLong("exp"),
                        members(rs.getArray("members"))));
    }

    private static List<Long> members(Array array) throws SQLException {
        List<Long> ids = new ArrayList<>();
        for (Object id : (Object[]) array.getArray()) {
            ids.add(((Number) id).longValue());
        }
        return ids;
    }

    /** Khoá dòng bang; trả cấp hiện tại. */
    private static int lockGuildLevel(Connection connection, long guildId) {
        return JdbcOperations.queryOne(connection, LOCK_GUILD_EXP, rs -> rs.getInt("level"), guildId)
                .orElseThrow(() -> new DatabaseAccessException("Bang " + guildId + " biến mất giữa giao dịch", null));
    }

    /** Cộng kinh nghiệm (dòng đã khoá), tính lại cấp; trả cấp mới. */
    private static int addGuildExp(Connection connection, long guildId, long amount) {
        long exp = JdbcOperations.queryOne(connection, LOCK_GUILD_EXP, rs -> rs.getLong("exp"), guildId)
                .orElse(0L);
        long newExp = exp + amount;
        int level = GuildLevelTable.levelFor(newExp);
        JdbcOperations.update(connection, SET_GUILD_EXP, newExp, level, guildId);
        return level;
    }
}
