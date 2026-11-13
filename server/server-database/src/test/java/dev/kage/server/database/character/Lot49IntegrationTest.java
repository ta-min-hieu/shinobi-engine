package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
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
 * Lô 49 trên Postgres thật: ghi chú thành viên bang (ADR-319), giới thiệu (ADR-320), lịch sử đột phá (ADR-322), V175.
 */
@Testcontainers
final class Lot49IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot49tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void referralRegistersOnceAndRewardsOnce() {
        long referrer = createFreshCharacter("RfOld");
        long referred = createFreshCharacter("RfNew");
        PostgresReferralAdapter adapter = new PostgresReferralAdapter(dataSource);
        assertThat(adapter.register(referred, referrer)).isTrue();
        assertThat(adapter.register(referred, referrer)).isFalse();
        assertThat(adapter.claimReward(referred)).contains(referrer);
        assertThat(adapter.claimReward(referred)).isEmpty();
        assertThat(adapter.claimReward(referrer)).isEmpty();
    }

    @Test
    void recordsBreakthroughsOldestFirst() {
        long character = createFreshCharacter("BtHero");
        PostgresBreakthroughLogAdapter adapter = new PostgresBreakthroughLogAdapter(dataSource);
        adapter.record(character, 1, 19);
        adapter.record(character, 2, 37);
        List<dev.kage.character.domain.BreakthroughLogPort.Entry> history = adapter.history(character);
        assertThat(history)
                .extracting(dev.kage.character.domain.BreakthroughLogPort.Entry::realm)
                .containsExactly(1, 2);
        assertThat(history.get(1).level()).isEqualTo(37);
    }

    @Test
    void memberNoteOnlyForMembers() {
        long leader = createFreshCharacter("GnLeader");
        long outsider = createFreshCharacter("GnOutside");
        execute("INSERT INTO guild (name, leader_character_id, created_at) VALUES ('GnBang', ?, now())", leader);
        execute(
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) "
                        + "SELECT id, leader_character_id, 'LEADER', now() FROM guild WHERE leader_character_id = ?",
                leader);
        long guildId = guildIdOf(leader);
        dev.kage.server.database.guild.PostgresGuildMemberNoteAdapter adapter =
                new dev.kage.server.database.guild.PostgresGuildMemberNoteAdapter(dataSource);
        assertThat(adapter.setNote(guildId, leader, "trụ cột")).isTrue();
        assertThat(adapter.setNote(guildId, outsider, "x")).isFalse();
        assertThat(adapter.notesOf(guildId)).containsEntry(leader, "trụ cột").hasSize(1);
        adapter.setNote(guildId, leader, "");
        assertThat(adapter.notesOf(guildId)).isEmpty();
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
