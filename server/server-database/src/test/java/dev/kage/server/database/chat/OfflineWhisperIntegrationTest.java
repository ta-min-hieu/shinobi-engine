package dev.kage.server.database.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import dev.kage.chat.domain.OfflineWhisperPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-187 — hộp tin riêng chờ trên Postgres thật: trần mỗi người nhận, lấy ra theo thứ tự gửi rồi xoá, bỏ tin quá
 * hạn.
 */
@Testcontainers
final class OfflineWhisperIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "offlinewhispertester"));
        accountId = accountHolder[0].id();
    }

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void capPerRecipient_takeInOrderOnce_dropExpired() {
        long sender = createFreshCharacter("OwSender");
        long recipient = createFreshCharacter("OwRecipient");
        PostgresOfflineWhisperAdapter adapter = new PostgresOfflineWhisperAdapter(dataSource);

        assertThat(adapter.store(sender, recipient, "cũ quá", NOW.minus(Duration.ofDays(8)), 3))
                .isTrue();
        assertThat(adapter.store(sender, recipient, "một", NOW, 3)).isTrue();
        assertThat(adapter.store(sender, recipient, "hai", NOW.plusSeconds(1), 3))
                .isTrue();
        assertThat(adapter.store(sender, recipient, "ba", NOW.plusSeconds(2), 3))
                .isFalse(); // đầy (3 tin)

        List<OfflineWhisperPort.Pending> taken = adapter.takeAll(recipient, NOW.minus(Duration.ofDays(7)));
        assertThat(taken).extracting(OfflineWhisperPort.Pending::body).containsExactly("một", "hai");
        assertThat(taken.get(0).senderName()).isEqualTo("OwSender");
        assertThat(adapter.takeAll(recipient, NOW.minus(Duration.ofDays(7)))).isEmpty(); // đã xoá
        assertThat(adapter.store(sender, recipient, "bốn", NOW, 3)).isTrue(); // hộp trống lại
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
