package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.SplittableRandom;
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
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.RefinementPolicy;
import dev.kage.inventory.domain.RefinementPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Tẩy luyện pháp khí (ADR-111, V91) trên PostgreSQL THẬT. */
@Testcontainers
class RefinementIntegrationTest {

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
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "refinetester"));
        accountId = holder[0].id();
    }

    private static long character(String name) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }

    private static long item(long characterId, String code, int quantity, int slotIndex) {
        return JdbcOperations.queryOne(dataSource, """
                        INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                        SELECT ?, id, ?, ?, 0 FROM item_template WHERE code = ? RETURNING id
                        """, rs -> rs.getLong(1), characterId, quantity, slotIndex, code)
                .orElseThrow();
    }

    private static void wear(long characterId, long itemId) {
        JdbcOperations.update(
                dataSource,
                "INSERT INTO equipment_slot (character_id, slot_type, inventory_item_id) VALUES (?, 'ARMOR', ?)"
                        + " ON CONFLICT (character_id, slot_type)"
                        + " DO UPDATE SET inventory_item_id = EXCLUDED.inventory_item_id",
                characterId,
                itemId);
    }

    private static long stones(long characterId) {
        return JdbcOperations.queryOne(dataSource, """
                        SELECT COALESCE(SUM(ii.quantity), 0) FROM inventory_item ii
                          JOIN item_template it ON it.id = ii.item_template_id
                         WHERE ii.character_id = ? AND it.code = 'refine_stone'
                        """, rs -> rs.getLong(1), characterId)
                .orElseThrow();
    }

    @Test
    void refineShouldSeedLinesSpendStonesAndKeepTheLockedLine() {
        long id = character("RefineA1");
        long vest = item(id, "novice_vest", 1, 0);
        item(id, RefinementPolicy.STONE_CODE, 5, 1);
        PostgresRefinementAdapter refinement = new PostgresRefinementAdapter(dataSource, new SplittableRandom(7));

        assertThat(refinement.refine(id, vest, RefinementPolicy.NO_LOCK).outcome())
                .isEqualTo(RefinementPort.Outcome.NOT_REFINABLE);
        wear(id, vest);
        assertThat(refinement.refine(id, vest, 0).outcome()).isEqualTo(RefinementPort.Outcome.INVALID_LOCK);
        assertThat(stones(id)).isEqualTo(5L);

        RefinementPort.Result first = refinement.refine(id, vest, RefinementPolicy.NO_LOCK);
        assertThat(first.outcome()).isEqualTo(RefinementPort.Outcome.OK);
        assertThat(first.lines()).hasSizeBetween(RefinementPolicy.MIN_LINES, RefinementPolicy.MAX_LINES);
        assertThat(stones(id)).isEqualTo(4L);
        assertThat(refinement.wornBonusOf(id)).isEqualTo(RefinementPolicy.bonusOf(first.lines()));

        RefinementPort.Result second = refinement.refine(id, vest, 0);
        assertThat(second.outcome()).isEqualTo(RefinementPort.Outcome.OK);
        assertThat(second.lines()).hasSameSizeAs(first.lines());
        assertThat(second.lines().get(0)).isEqualTo(first.lines().get(0));
        assertThat(stones(id)).isEqualTo(1L);

        assertThat(refinement.refine(id, vest, 0).outcome()).isEqualTo(RefinementPort.Outcome.NOT_ENOUGH_STONES);
        assertThat(stones(id)).isEqualTo(1L);

        List<RefinementPort.WornItem> worn = refinement.wornItems(id);
        assertThat(worn).singleElement().satisfies(w -> assertThat(w.lines()).isEqualTo(second.lines()));
        assertThat(JdbcOperations.queryOne(
                                dataSource,
                                "SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'ITEM_REFINED'",
                                rs -> rs.getLong(1),
                                id)
                        .orElseThrow())
                .isEqualTo(2L);
    }

    @Test
    void refinedItemShouldStopCountingWhenTakenOffAndBeRefusedByStorage() {
        long id = character("RefineB1");
        long vest = item(id, "novice_vest", 1, 0);
        item(id, RefinementPolicy.STONE_CODE, 1, 1);
        wear(id, vest);
        PostgresRefinementAdapter refinement = new PostgresRefinementAdapter(dataSource, new SplittableRandom(3));
        assertThat(refinement.refine(id, vest, RefinementPolicy.NO_LOCK).outcome())
                .isEqualTo(RefinementPort.Outcome.OK);
        JdbcOperations.update(
                dataSource, "UPDATE equipment_slot SET inventory_item_id = NULL WHERE character_id = ?", id);

        assertThat(refinement.wornBonusOf(id)).isEqualTo(EquipmentBonus.NONE);
        boolean[] bound = new boolean[1];
        new JdbcInventoryTransactionRunner(dataSource)
                .inTransaction(tx -> bound[0] = new PostgresInventoryAdapter(dataSource).isSocketed(tx, vest));
        assertThat(bound[0]).isTrue();
    }
}
