package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import dev.kage.inventory.domain.GemCombinePort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-161 — ghép Linh Ngọc trên Postgres thật: 3 viên + vàng ⇒ 1 viên bậc trên; thiếu vàng thì không mất ngọc; ngọc
 * đã khoá không bị đem ghép (ADR-159).
 */
@Testcontainers
final class GemCombineIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final long FEE = 2000L;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long fire;
    static long fire2;
    static PostgresGemCombineAdapter adapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "gemcombinetester"));
        accountId = accountHolder[0].id();
        fire = queryLong("SELECT id FROM item_template WHERE code = ?", "spirit_jade_fire");
        fire2 = queryLong("SELECT id FROM item_template WHERE code = ?", "spirit_jade_fire_2");
        adapter = new PostgresGemCombineAdapter(dataSource);
    }

    @Test
    void threeGemsAndGoldShouldBecomeOneGemOfTheNextGrade() {
        long owner = createFreshCharacter("GemA");
        insert(owner, fire, 4);
        setGold(owner, FEE + 5);

        assertThat(adapter.combine(owner, fire)).isEqualTo(GemCombinePort.Outcome.OK);

        assertThat(count(owner, fire)).isEqualTo(1L);
        assertThat(count(owner, fire2)).isEqualTo(1L);
        assertThat(queryLong("SELECT gold FROM character_stat WHERE character_id = ?", owner))
                .isEqualTo(5L);
        assertThat(adapter.recipes(owner))
                .filteredOn(r -> r.sourceItemTemplateId() == fire)
                .singleElement()
                .extracting(GemCombinePort.Recipe::owned)
                .isEqualTo(1);
    }

    @Test
    void notEnoughGoldShouldKeepTheGems() {
        long owner = createFreshCharacter("GemB");
        insert(owner, fire, 3);
        setGold(owner, FEE - 1);

        assertThat(adapter.combine(owner, fire)).isEqualTo(GemCombinePort.Outcome.NOT_ENOUGH_GOLD);

        assertThat(count(owner, fire)).isEqualTo(3L);
        assertThat(count(owner, fire2)).isZero();
    }

    @Test
    void lockedGemsShouldNotBeCombined_andTopGradeHasNoRecipe() {
        long owner = createFreshCharacter("GemC");
        long stack = insert(owner, fire, 3);
        new PostgresItemLockAdapter(dataSource).setLocked(owner, stack, true);
        setGold(owner, FEE);

        assertThat(adapter.combine(owner, fire)).isEqualTo(GemCombinePort.Outcome.NOT_ENOUGH_GEMS);
        assertThat(count(owner, fire)).isEqualTo(3L);
        long fire3 = queryLong("SELECT id FROM item_template WHERE code = ?", "spirit_jade_fire_3");
        assertThat(adapter.combine(owner, fire3)).isEqualTo(GemCombinePort.Outcome.UNKNOWN_RECIPE);
    }

    private static long insert(long characterId, long templateId, int quantity) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, ?, 0, 0) RETURNING id
                """, characterId, templateId, quantity);
    }

    private static long count(long characterId, long templateId) {
        return queryLong(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
    }

    private static void setGold(long characterId, long gold) {
        queryLong("UPDATE character_stat SET gold = ? WHERE character_id = ? RETURNING gold", gold, characterId);
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
