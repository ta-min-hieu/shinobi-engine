package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * Lô 52 trên Postgres thật: tra tên cũ (ADR-343), ký hiệu bang (ADR-344), V178.
 */
@Testcontainers
final class Lot52IntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot52tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void nameHistoryNewestFirstAndByOldName() {
        long character = createFreshCharacter("NhNow");
        executeText("INSERT INTO character_name_history (character_id, old_name, new_name, changed_at) " + "VALUES ("
                + character + ", 'NhFirst', 'NhMid', now() - interval '2 day')");
        executeText("INSERT INTO character_name_history (character_id, old_name, new_name, changed_at) " + "VALUES ("
                + character + ", 'NhMid', 'NhNow', now())");
        PostgresNameHistoryAdapter adapter = new PostgresNameHistoryAdapter(dataSource);
        assertThat(adapter.history(character))
                .extracting(dev.kage.character.domain.NameHistoryPort.Entry::oldName)
                .containsExactly("NhMid", "NhFirst");
        assertThat(adapter.characterWithOldName("nhfirst")).contains(character);
        assertThat(adapter.characterWithOldName("Nobody")).isEmpty();
    }

    @Test
    void guildTagUniqueAndReadableByMember() {
        long leader = createFreshCharacter("GtLeader");
        long other = createFreshCharacter("GtOther");
        execute("INSERT INTO guild (name, leader_character_id, created_at) VALUES ('GtBang', ?, now())", leader);
        execute("INSERT INTO guild (name, leader_character_id, created_at) VALUES ('GtKhac', ?, now())", other);
        execute(
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) "
                        + "SELECT id, leader_character_id, 'LEADER', now() FROM guild WHERE leader_character_id = ?",
                leader);
        long guildId = guildIdOf(leader);
        long otherGuild = guildIdOf(other);
        dev.kage.server.database.guild.PostgresGuildTagAdapter adapter =
                new dev.kage.server.database.guild.PostgresGuildTagAdapter(dataSource);
        assertThat(adapter.setTag(guildId, "KAGE")).isTrue();
        assertThat(adapter.setTag(otherGuild, "kage")).isFalse();
        assertThat(adapter.tagOf(guildId)).contains("KAGE");
        assertThat(adapter.tagOfCharacter(leader)).contains("KAGE");
        assertThat(adapter.tagOfCharacter(other)).isEmpty();
        assertThat(adapter.setTag(guildId, null)).isTrue();
        assertThat(adapter.tagOf(guildId)).isEmpty();
    }

    private static void executeText(String sql) {
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long guildIdOf(long leader) {
        return dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource, "SELECT id FROM guild WHERE leader_character_id = ?", rs -> rs.getLong(1), leader)
                .orElseThrow();
    }

    private static void execute(String sql, long id) {
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
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
}
