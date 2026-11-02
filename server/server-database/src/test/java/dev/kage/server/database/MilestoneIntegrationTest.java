package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
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
import dev.kage.character.domain.MilestonePort;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.character.PostgresMilestoneAdapter;

/**
 * ADR-153 — quà mốc cảnh giới trên PostgreSQL THẬT (V124): chưa đủ cấp thì không nhận, nhận đúng một lần, túi đầy
 * thì quà vẫn chờ.
 */
@Testcontainers
class MilestoneIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static PostgresMilestoneAdapter milestones;

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
        milestones = new PostgresMilestoneAdapter(dataSource);
    }

    @Test
    void shouldClaimOnlyReachedMilestonesExactlyOnce() {
        long hero = character("MocGioi01");
        JdbcOperations.update(dataSource, "UPDATE character SET level = 20 WHERE id = ?", hero);
        JdbcOperations.update(dataSource, "UPDATE character_stat SET gold = 0 WHERE character_id = ?", hero);

        MilestonePort.Board board = milestones.board(hero);
        assertThat(board.characterLevel()).isEqualTo(20);
        assertThat(board.milestones())
                .extracting(MilestonePort.Milestone::level)
                .containsExactly(10, 19, 37, 55, 73, 91);

        assertThat(milestones.claim(hero, 37, NOW)).isEqualTo(MilestonePort.Outcome.LEVEL_TOO_LOW);
        assertThat(milestones.claim(hero, 11, NOW)).isEqualTo(MilestonePort.Outcome.UNKNOWN_MILESTONE);
        assertThat(milestones.claim(hero, 10, NOW)).isEqualTo(MilestonePort.Outcome.OK);
        assertThat(milestones.claim(hero, 10, NOW)).isEqualTo(MilestonePort.Outcome.ALREADY_CLAIMED);
        assertThat(milestones.claim(hero, 19, NOW)).isEqualTo(MilestonePort.Outcome.OK);

        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", hero))
                .isEqualTo(4000L);
        assertThat(milestones.board(hero).milestones())
                .filteredOn(MilestonePort.Milestone::claimed)
                .extracting(MilestonePort.Milestone::level)
                .containsExactly(10, 19);
        assertThat(scalar("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'MILESTONE_CLAIM'", hero))
                .isEqualTo(2L);
    }

    @Test
    void fullBagShouldKeepTheGiftWaiting() {
        long hero = character("MocGioi02");
        JdbcOperations.update(dataSource, "UPDATE character SET level = 12 WHERE id = ?", hero);
        for (int slot = 0; slot < 20; slot++) {
            JdbcOperations.update(dataSource, """
                    INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                    SELECT ?, id, 1, ?, 0 FROM item_template WHERE code = 'kunai'
                    """, hero, slot);
        }

        assertThat(milestones.claim(hero, 10, NOW)).isEqualTo(MilestonePort.Outcome.BAG_FULL);
        assertThat(milestones.board(hero).milestones().get(0).claimed()).isFalse();
    }

    private static long scalar(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    private static long character(String name) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] account = new Account[1];
        accountTx.inTransaction(tx -> account[0] = accountAdapter.findOrCreate(tx, name.toLowerCase(Locale.ROOT)));
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(
                        account[0].id(), name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }
}
