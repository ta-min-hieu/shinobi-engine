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

/** Lô 64 trên Postgres thật: xếp hạng linh thú / cường hoá / nhiệm vụ / hạ boss (ADR-439…442). */
@Testcontainers
final class Lot64IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot64tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void ranksPetsAndEnhancementPerCharacter() {
        long a = createFreshCharacter("GrA");
        long b = createFreshCharacter("GrB");
        long template = JdbcOperations.queryOne(
                        dataSource, "SELECT id FROM item_template ORDER BY id LIMIT 1", rs -> rs.getLong(1))
                .orElseThrow();
        long itemA1 = insertItem(a, template, 9);
        long itemA2 = insertItem(a, template, 3);
        long itemB = insertItem(b, template, 0);
        executeText("INSERT INTO pet_state (inventory_item_id, level, exp) VALUES (" + itemA1 + ", 4, 0), (" + itemA2
                + ", 7, 0), (" + itemB + ", 29, 0)");
        dev.kage.server.database.inventory.PostgresGearRankingAdapter adapter =
                new dev.kage.server.database.inventory.PostgresGearRankingAdapter(dataSource);
        assertThat(adapter.topPets(2))
                .containsExactly(
                        new dev.kage.inventory.domain.GearRankingPort.Ranked("GrB", 29),
                        new dev.kage.inventory.domain.GearRankingPort.Ranked("GrA", 7));
        assertThat(adapter.topEnhance(10))
                .contains(new dev.kage.inventory.domain.GearRankingPort.Ranked("GrA", 9))
                .extracting(dev.kage.inventory.domain.GearRankingPort.Ranked::name)
                .doesNotContain("GrB");
    }

    @Test
    void ranksCompletedQuestsAndBossKills() {
        long a = createFreshCharacter("QbA");
        long quest = JdbcOperations.queryOne(
                        dataSource, "SELECT id FROM quest_template ORDER BY id LIMIT 1", rs -> rs.getLong(1))
                .orElseThrow();
        executeText(
                "INSERT INTO character_quest_progress (character_id, quest_template_id, status, progress_json, updated_at)"
                        + " VALUES (" + a + ", " + quest + ", 'CLAIMED', '{}', now())");
        executeText("INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (" + a
                + ", 'BOSS_KILL', '{}', now()), (" + a + ", 'BOSS_KILL', '{}', now()), (" + a
                + ", 'OTHER', '{}', now())");
        assertThat(new dev.kage.server.database.quest.PostgresQuestRankingAdapter(dataSource).top(50))
                .contains(new dev.kage.quest.domain.QuestRankingPort.Ranked("QbA", 1));
        assertThat(new dev.kage.server.database.monster.PostgresBossKillRankingAdapter(dataSource).top(50))
                .contains(new dev.kage.monster.domain.BossKillRankingPort.Ranked("QbA", 2));
    }

    private static long insertItem(long characterId, long template, int enhance) {
        executeText(
                "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index) VALUES ("
                        + characterId + ", " + template + ", 1, " + enhance + ", " + enhance + ")");
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT max(id) FROM inventory_item WHERE character_id = " + characterId,
                        rs -> rs.getLong(1))
                .orElseThrow();
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
