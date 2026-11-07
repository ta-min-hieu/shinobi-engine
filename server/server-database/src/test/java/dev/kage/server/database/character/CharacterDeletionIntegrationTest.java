package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * ADR-205 — xoá có chờ trên Postgres thật: đánh dấu, khôi phục khi chọn, quá hạn thì biến khỏi danh sách và không chiếm
 * chỗ.
 */
@Testcontainers
final class CharacterDeletionIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "deletiontester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void markRestoreAndExpire() {
        long keep = createFreshCharacter("DelKeep");
        long gone = createFreshCharacter("DelGone");
        PostgresCharacterDeletionAdapter adapter = new PostgresCharacterDeletionAdapter(dataSource);
        PostgresCharacterAdapter characters = new PostgresCharacterAdapter(dataSource);
        Instant now = Instant.now();
        Instant expiredBefore = now.minus(Duration.ofDays(3));

        assertThat(adapter.request(accountId + 999, keep, now, expiredBefore))
                .as("tài khoản khác")
                .isFalse();
        assertThat(adapter.request(accountId, keep, now, expiredBefore)).isTrue();
        assertThat(adapter.pendingOf(accountId, expiredBefore)).containsKey(keep);
        assertThat(adapter.cancelIfPending(accountId, keep, expiredBefore))
                .isEqualTo(dev.kage.character.domain.CharacterDeletionPort.State.PENDING);
        assertThat(adapter.pendingOf(accountId, expiredBefore)).doesNotContainKey(keep);

        int before = characters.countByAccount(accountId);
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "UPDATE character SET deletion_requested_at = now() - interval '4 days' WHERE id = ?",
                gone);
        assertThat(adapter.cancelIfPending(accountId, gone, expiredBefore))
                .isEqualTo(dev.kage.character.domain.CharacterDeletionPort.State.EXPIRED);
        assertThat(characters.listByAccount(accountId))
                .extracting(Character::id)
                .contains(keep)
                .doesNotContain(gone);
        assertThat(characters.countByAccount(accountId))
                .as("quá hạn — không chiếm chỗ")
                .isEqualTo(before - 1);
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
