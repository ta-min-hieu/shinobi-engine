package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
import dev.kage.inventory.domain.ItemStack;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-160 — sắp xếp túi trên Postgres thật: gom chồng, thứ tự mới được ĐỌC lại đúng (PostgresInventoryAdapter liệt kê
 * theo slot_index), dòng thêm sau khi sắp vẫn nối CUỐI túi, và dòng khoá không bị gom vào dòng không khoá.
 */
@Testcontainers
final class BagSortIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final long KUNAI = 1L;
    private static final long WOODEN_SWORD = 2L;
    private static final long PILL = 20L;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "bagsorttester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void sortShouldMergeStacksAndPutEquipmentFirst_thenNewRowsAppendAtTheEnd() {
        long owner = createFreshCharacter("BagSortA");
        long pillA = insert(owner, PILL, 15, 0);
        long kunai = insert(owner, KUNAI, 4, 1);
        long pillB = insert(owner, PILL, 9, 2);
        long sword = insert(owner, WOODEN_SWORD, 1, 3);

        int merged = new PostgresBagSortAdapter(dataSource).sort(owner);

        assertThat(merged).isZero(); // 15 + 9 = 24 > 20: hai dòng vẫn cần cả hai
        assertThat(templateOrder(owner)).containsExactly(WOODEN_SWORD, PILL, PILL, KUNAI);
        assertThat(queryLong("SELECT quantity FROM inventory_item WHERE id = ?", pillA))
                .isEqualTo(20L);
        assertThat(queryLong("SELECT quantity FROM inventory_item WHERE id = ?", pillB))
                .isEqualTo(4L);
        assertThat(rowExists(sword)).isTrue();

        JdbcTransactionRunner.call(
                dataSource,
                c -> InventoryStackMerge.insertRespectingMaxStack(c, owner, new ItemLine(WOODEN_SWORD, 1, 0)));

        List<Long> order = templateOrder(owner);
        assertThat(order).hasSize(5).last().isEqualTo(WOODEN_SWORD);
        assertThat(rowExists(kunai)).isTrue();
    }

    @Test
    void sortShouldDeleteEmptiedRows_butNeverMergeALockedStackIntoAnUnlockedOne() {
        long owner = createFreshCharacter("BagSortB");
        long first = insert(owner, PILL, 3, 0);
        long second = insert(owner, PILL, 4, 1);
        long locked = insert(owner, PILL, 2, 2);
        new PostgresItemLockAdapter(dataSource).setLocked(owner, locked, true);

        int merged = new PostgresBagSortAdapter(dataSource).sort(owner);

        assertThat(merged).isEqualTo(1);
        assertThat(queryLong("SELECT quantity FROM inventory_item WHERE id = ?", first))
                .isEqualTo(7L);
        assertThat(rowExists(second)).isFalse();
        assertThat(queryLong("SELECT quantity FROM inventory_item WHERE id = ?", locked))
                .isEqualTo(2L);
    }

    private static List<Long> templateOrder(long characterId) {
        return new PostgresInventoryAdapter(dataSource)
                .findByCharacter(characterId).orElseThrow().items().stream()
                        .map(ItemStack::itemTemplateId)
                        .toList();
    }

    private static long insert(long characterId, long templateId, int quantity, int slotIndex) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, ?, 0, ?) RETURNING id
                """, characterId, templateId, quantity, slotIndex);
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

    private static boolean rowExists(long itemId) {
        return queryLong("SELECT COUNT(*) FROM inventory_item WHERE id = ?", itemId) > 0;
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
