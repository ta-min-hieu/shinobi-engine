package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.guild.domain.GuildRenamePort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-172 — đổi tên bang trên Postgres thật: bang chủ đổi được (trừ vàng, ghi lúc đổi); lần hai trong 30 ngày bị hồi
 * chiêu; thành viên thường, người không có bang, tên trùng (không phân biệt hoa thường), thiếu vàng đều bị từ chối.
 */
@Testcontainers
final class GuildRenameIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant AT = Instant.parse("2026-11-04T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static PostgresGuildRenameAdapter adapter;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildrenametester"));
        accountId = accountHolder[0].id();
        adapter = new PostgresGuildRenameAdapter(dataSource);
    }

    @Test
    void leaderRenamesOnce_thenCooldown() {
        long leader = createFreshCharacter("GrLeadA");
        long guild = guild("BangCuA", leader);
        setGold(leader, GuildRenamePort.FEE + 5);

        GuildRenamePort.Renamed renamed = adapter.rename(leader, "BangMoiA", AT);

        assertThat(renamed.outcome()).isEqualTo(GuildRenamePort.Outcome.OK);
        assertThat(renamed.guildId()).isEqualTo(guild);
        assertThat(queryString(guild)).isEqualTo("BangMoiA");
        assertThat(gold(leader)).isEqualTo(5L);
        setGold(leader, GuildRenamePort.FEE);
        GuildRenamePort.Renamed again = adapter.rename(leader, "BangMoiB", AT.plusSeconds(60));
        assertThat(again.outcome()).isEqualTo(GuildRenamePort.Outcome.ON_COOLDOWN);
        assertThat(again.nextAllowedAt()).isEqualTo(AT.plus(GuildRenamePort.COOLDOWN));
        assertThat(adapter.rename(leader, "BangMoiB", AT.plus(GuildRenamePort.COOLDOWN))
                        .outcome())
                .isEqualTo(GuildRenamePort.Outcome.OK);
    }

    @Test
    void refusalsShouldChangeNothing() {
        long leader = createFreshCharacter("GrLeadB");
        long member = createFreshCharacter("GrMemB");
        long loner = createFreshCharacter("GrLoneB");
        long guild = guild("BangCuB", leader);
        guild("BangKhacB", createFreshCharacter("GrLeadC"));
        queryLong(
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) VALUES (?, ?, 'MEMBER', now())"
                        + " RETURNING guild_id",
                guild,
                member);
        setGold(leader, GuildRenamePort.FEE - 1);

        assertThat(adapter.rename(member, "TenMoi", AT).outcome()).isEqualTo(GuildRenamePort.Outcome.NOT_LEADER);
        assertThat(adapter.rename(loner, "TenMoi", AT).outcome()).isEqualTo(GuildRenamePort.Outcome.NOT_IN_GUILD);
        assertThat(adapter.rename(leader, "bangkhacb", AT).outcome()).isEqualTo(GuildRenamePort.Outcome.NAME_TAKEN);
        assertThat(adapter.rename(leader, "TenMoi", AT).outcome()).isEqualTo(GuildRenamePort.Outcome.NOT_ENOUGH_GOLD);
        assertThat(queryString(guild)).isEqualTo("BangCuB");
    }

    private static long guild(String name, long leader) {
        long id = queryLong(
                "INSERT INTO guild (name, leader_character_id, created_at) VALUES (?, ?, now()) RETURNING id",
                name,
                leader);
        queryLong(
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) VALUES (?, ?, 'LEADER', now())"
                        + " RETURNING guild_id",
                id,
                leader);
        return id;
    }

    private static String queryString(long guildId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT name FROM guild WHERE id = ?")) {
            statement.setLong(1, guildId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc tên bang", e);
        }
    }

    private static long gold(long characterId) {
        return queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
    }

    private static void setGold(long characterId, long gold) {
        queryLong("UPDATE character_stat SET gold = ? WHERE character_id = ? RETURNING gold", gold, characterId);
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only truy vấn: " + sql, e);
        }
    }
}
