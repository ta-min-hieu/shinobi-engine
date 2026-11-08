package dev.kage.server.database.friend;

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
import dev.kage.account.domain.LoginHistoryPort;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.friend.domain.ContactPrivacyPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Lô 38 (nửa sau) trên Postgres thật: công tắc riêng tư (ADR-236/237), lịch sử đăng nhập cắt bớt (ADR-238), hạn mức thư
 * toàn bang đếm trên audit (ADR-235).
 */
@Testcontainers
final class Lot38bIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot38btester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void storesBothPrivacySwitches() {
        long owner = createFreshCharacter("PvOwner");
        PostgresContactPrivacyAdapter adapter = new PostgresContactPrivacyAdapter(dataSource);

        assertThat(adapter.settings(owner)).isEqualTo(ContactPrivacyPort.Settings.NONE);
        adapter.setDoNotDisturb(owner, true);
        assertThat(adapter.settings(owner)).isEqualTo(new ContactPrivacyPort.Settings(true, false));
        adapter.setBlockStrangerInvites(owner, true);
        adapter.setDoNotDisturb(owner, false);
        assertThat(adapter.settings(owner)).isEqualTo(new ContactPrivacyPort.Settings(false, true));
        assertThat(adapter.settings(owner + 999)).isEqualTo(ContactPrivacyPort.Settings.NONE);
    }

    @Test
    void keepsOnlyTheNewestLogins() {
        dev.kage.server.database.account.PostgresLoginHistoryAdapter adapter =
                new dev.kage.server.database.account.PostgresLoginHistoryAdapter(dataSource);
        Instant start = Instant.parse("2026-11-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            adapter.record(accountId, "10." + i + ".x.x", start.plusSeconds(i), 3);
        }

        List<LoginHistoryPort.Entry> recent = adapter.recent(accountId, 10);
        assertThat(recent)
                .extracting(LoginHistoryPort.Entry::maskedIp)
                .containsExactly("10.4.x.x", "10.3.x.x", "10.2.x.x");
        assertThat(single("SELECT COUNT(*) FROM login_history WHERE account_id = ?", accountId))
                .isEqualTo(3L);
    }

    @Test
    void countsGuildMailBatchesPerGuildSince() {
        long sender = createFreshCharacter("GmSender");
        dev.kage.server.database.guild.PostgresGuildMailQuotaAdapter adapter =
                new dev.kage.server.database.guild.PostgresGuildMailQuotaAdapter(dataSource);
        Instant now = Instant.now();

        adapter.record(77L, sender, 5, now);
        adapter.record(77L, sender, 5, now);
        adapter.record(78L, sender, 2, now);

        assertThat(adapter.sentSince(77L, now.minus(Duration.ofHours(1)))).isEqualTo(2);
        assertThat(adapter.sentSince(78L, now.minus(Duration.ofHours(1)))).isEqualTo(1);
        assertThat(adapter.sentSince(77L, now.plus(Duration.ofHours(1)))).isZero();
    }

    private static long single(String sql, Object arg) {
        return dev.kage.server.database.JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), arg)
                .orElseThrow();
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
