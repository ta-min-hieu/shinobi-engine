package dev.kage.server.database.guild;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildRenamePort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GuildRenamePort} (ADR-172, V138). Khoá dòng bang ({@code FOR UPDATE}) để hai lệnh đổi song song
 * không cùng lọt hồi chiêu; tên trùng kiểm trước (không phân biệt hoa thường, như chỉ mục {@code guild_name_lower_key})
 * và chỉ mục vẫn là chốt cuối.
 */
public final class PostgresGuildRenameAdapter implements GuildRenamePort {

    private static final String GOLD_REASON = "gold.reason.guild_rename";

    private static final String MEMBERSHIP = "SELECT guild_id, role FROM guild_member WHERE character_id = ?";

    private static final String LOCK_GUILD = "SELECT name, renamed_at FROM guild WHERE id = ? FOR UPDATE";

    private static final String NAME_TAKEN = "SELECT COUNT(*) FROM guild WHERE lower(name) = lower(?) AND id <> ?";

    private static final String RENAME = "UPDATE guild SET name = ?, renamed_at = ? WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Thành viên bang. */
    private record Membership(long guildId, String role) {}

    /** Một lệnh đổi tên. */
    private record Request(long characterId, String newName, Instant at) {}

    private final DataSource dataSource;

    public PostgresGuildRenameAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Renamed rename(long characterId, String newName, Instant at) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> renameIn(connection, new Request(characterId, newName, at)));
        } catch (Abort abort) {
            return new Renamed(abort.outcome, "", 0L, abort.nextAllowedAt);
        }
    }

    private static Renamed renameIn(Connection connection, Request request) {
        long guildId = lockLeadersGuild(connection, request);
        if (JdbcOperations.queryOne(connection, NAME_TAKEN, rs -> rs.getInt(1), request.newName(), guildId)
                        .orElse(0)
                > 0) {
            throw new Abort(Outcome.NAME_TAKEN, Instant.EPOCH);
        }
        if (!GoldLedger.deduct(connection, request.characterId(), FEE, GOLD_REASON)) {
            throw new Abort(Outcome.NOT_ENOUGH_GOLD, Instant.EPOCH);
        }
        JdbcOperations.update(connection, RENAME, request.newName(), Timestamp.from(request.at()), guildId);
        JdbcOperations.update(
                connection,
                AUDIT,
                request.characterId(),
                "GUILD_RENAMED",
                "{\"guildId\":" + guildId + ",\"to\":\"" + request.newName() + "\"}",
                Timestamp.from(request.at()));
        GuildLog.append(
                connection,
                new GuildLog.Entry(guildId, GuildLog.Kind.RENAMED, request.characterId(), 0L, 0L, request.newName()),
                request.at()); // ADR-180
        return new Renamed(Outcome.OK, request.newName(), guildId, request.at().plus(COOLDOWN));
    }

    /** Bang của người gửi — phải là bang chủ; khoá dòng bang và kiểm hồi chiêu. Trả id bang. */
    private static long lockLeadersGuild(Connection connection, Request request) {
        Membership membership = JdbcOperations.queryOne(
                        connection,
                        MEMBERSHIP,
                        rs -> new Membership(rs.getLong(1), rs.getString(2)),
                        request.characterId())
                .orElseThrow(() -> new Abort(Outcome.NOT_IN_GUILD, Instant.EPOCH));
        if (!"LEADER".equals(membership.role())) {
            throw new Abort(Outcome.NOT_LEADER, Instant.EPOCH);
        }
        Optional<Instant> renamedAt = JdbcOperations.queryOne(
                        connection,
                        LOCK_GUILD,
                        rs -> Optional.ofNullable(rs.getTimestamp(2)).map(Timestamp::toInstant),
                        membership.guildId())
                .orElseThrow(() -> new Abort(Outcome.NOT_IN_GUILD, Instant.EPOCH));
        Optional<Instant> next = renamedAt.map(last -> last.plus(COOLDOWN));
        if (next.isPresent() && request.at().isBefore(next.get())) {
            throw new Abort(Outcome.ON_COOLDOWN, next.get());
        }
        return membership.guildId();
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;
        private final transient Instant nextAllowedAt;

        Abort(Outcome outcome, Instant nextAllowedAt) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
            this.nextAllowedAt = nextAllowedAt;
        }
    }
}
