package dev.kage.server.database.inventory;

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
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.character.PostgresExpBuffAdapter;

/**
 * ADR-164 — đan x2 kinh nghiệm trên Postgres thật: dùng đan ghi hạn, dùng tiếp DỒN thời gian, chạm trần 300 phút thì
 * từ chối và không đổi gì.
 */
@Testcontainers
final class DoubleExpIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int PILL_MINUTES = 30;
    private static final long SLACK_SECONDS = 120L;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "doubleexptester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void pillsShouldStackUntilTheCap() {
        long owner = createFreshCharacter("ExpA");
        PostgresExpBuffAdapter reader = new PostgresExpBuffAdapter(dataSource);
        assertThat(reader.expiresAt(owner)).isEmpty();

        assertThat(extend(owner)).isTrue();
        assertThat(extend(owner)).isTrue();

        Instant expiry = reader.expiresAt(owner).orElseThrow();
        assertThat(Duration.between(Instant.now(), expiry).toSeconds())
                .isBetween(2L * PILL_MINUTES * 60 - SLACK_SECONDS, 2L * PILL_MINUTES * 60 + SLACK_SECONDS);
        for (int i = 2; i < 10; i++) {
            assertThat(extend(owner)).as("viên thứ %d", i + 1).isTrue();
        }
        assertThat(extend(owner)).as("viên thứ 11 vượt trần 300 phút").isFalse();
        assertThat(Duration.between(Instant.now(), reader.expiresAt(owner).orElseThrow())
                        .toMinutes())
                .isLessThanOrEqualTo(300L);
    }

    private static boolean extend(long characterId) {
        boolean[] done = new boolean[1];
        new JdbcInventoryTransactionRunner(dataSource)
                .inTransaction(tx -> done[0] =
                        new PostgresInventoryAdapter(dataSource).extendDoubleExp(tx, characterId, PILL_MINUTES));
        return done[0];
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
