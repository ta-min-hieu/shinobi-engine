package dev.kage.server.database;

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
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Lô 59 trên Postgres thật: tìm bang (ADR-399), báo đã đọc thư (ADR-400), chỉ bạn bè nhắn riêng (ADR-402), V183. */
@Testcontainers
final class Lot59IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot59tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void searchesGuildsByFragment() {
        long leader = createFreshCharacter("GsLeader");
        executeText("INSERT INTO guild (name, leader_character_id, created_at, level) VALUES ('Thanh_Van Mon', "
                + leader + ", now(), 3)");
        executeText("INSERT INTO guild_member (guild_id, character_id, role, joined_at) SELECT id, " + leader
                + ", 'LEADER', now() FROM guild WHERE name = 'Thanh_Van Mon'");
        dev.kage.server.database.guild.PostgresGuildDirectoryAdapter adapter =
                new dev.kage.server.database.guild.PostgresGuildDirectoryAdapter(dataSource);
        assertThat(adapter.search("thanh_van", 10))
                .containsExactly(
                        new dev.kage.guild.domain.GuildDirectoryPort.Found("Thanh_Van Mon", 3, 1, "GsLeader", ""));
        assertThat(adapter.search("%", 10)).as("ký tự đại diện được thoát").isEmpty();
    }

    @Test
    void receiptOnlyForPlayerMail() {
        long sender = createFreshCharacter("RcSender");
        long owner = createFreshCharacter("RcOwner");
        executeText("INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at) VALUES ("
                + sender + ", " + owner + ", 'chào', 'b', 0, now()), (NULL, " + owner + ", 'hệ thống', 'b', 0, now())");
        dev.kage.server.database.mail.PostgresMailReceiptAdapter adapter =
                new dev.kage.server.database.mail.PostgresMailReceiptAdapter(dataSource);
        long playerMail = JdbcOperations.queryOne(
                        dataSource,
                        "SELECT id FROM mail WHERE subject = 'chào' AND recipient_id = ?",
                        rs -> rs.getLong(1),
                        owner)
                .orElseThrow();
        long systemMail = JdbcOperations.queryOne(
                        dataSource,
                        "SELECT id FROM mail WHERE sender_id IS NULL AND recipient_id = ?",
                        rs -> rs.getLong(1),
                        owner)
                .orElseThrow();
        assertThat(adapter.receipt(playerMail, owner))
                .contains(new dev.kage.mail.domain.MailReceiptPort.Receipt(sender, "chào"));
        assertThat(adapter.receipt(systemMail, owner)).isEmpty();
        assertThat(adapter.receipt(playerMail, sender))
                .as("không phải người nhận")
                .isEmpty();
    }

    @Test
    void whisperFriendsOnlyToggles() {
        long id = createFreshCharacter("WfA");
        dev.kage.server.database.friend.PostgresWhisperPrivacyAdapter adapter =
                new dev.kage.server.database.friend.PostgresWhisperPrivacyAdapter(dataSource);
        assertThat(adapter.friendsOnly(id)).isFalse();
        adapter.setFriendsOnly(id, true);
        adapter.setFriendsOnly(id, true);
        assertThat(adapter.friendsOnly(id)).isTrue();
        adapter.setFriendsOnly(id, false);
        assertThat(adapter.friendsOnly(id)).isFalse();
    }

    private static void executeText(String sql) {
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
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
