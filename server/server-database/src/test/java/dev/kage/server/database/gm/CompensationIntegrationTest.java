package dev.kage.server.database.gm;

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
import dev.kage.gm.domain.CompensationPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-165 — sổ đợt bồi thường trên Postgres thật: đợt mới; cùng mã cùng quà chưa xong ⇒ chạy tiếp; cùng mã khác quà ⇒
 * xung đột; xong rồi ⇒ không chạy lại; người nhận lọc theo cấp.
 */
@Testcontainers
final class CompensationIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant AT = Instant.parse("2026-11-03T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static PostgresCompensationAdapter adapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "compensationtester"));
        accountId = accountHolder[0].id();
        adapter = new PostgresCompensationAdapter(dataSource);
    }

    @Test
    void campaignShouldStartResumeConflictAndFinish() {
        CompensationPort.Campaign campaign = new CompensationPort.Campaign("MAINT01", 500L, 0L, 0, 1);

        assertThat(adapter.begin(campaign, 1L, AT)).isEqualTo(CompensationPort.Start.NEW);
        assertThat(adapter.begin(campaign, 1L, AT)).isEqualTo(CompensationPort.Start.RESUME);
        assertThat(adapter.begin(new CompensationPort.Campaign("MAINT01", 900L, 0L, 0, 1), 1L, AT))
                .isEqualTo(CompensationPort.Start.CONFLICT);
        adapter.finish("MAINT01", 3, AT);
        assertThat(adapter.begin(campaign, 1L, AT)).isEqualTo(CompensationPort.Start.DONE);
        assertThat(queryLong("SELECT sent FROM gm_compensation WHERE code = ?", "MAINT01"))
                .isEqualTo(3L);
    }

    @Test
    void recipientsShouldBeFilteredByLevel() {
        long low = createFreshCharacter("CompLow");
        long high = createFreshCharacter("CompHigh");
        queryLong("UPDATE character SET level = 30 WHERE id = ? RETURNING id", high);

        assertThat(adapter.recipients(30)).contains(high).doesNotContain(low);
        assertThat(adapter.recipients(1)).contains(low, high);
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
