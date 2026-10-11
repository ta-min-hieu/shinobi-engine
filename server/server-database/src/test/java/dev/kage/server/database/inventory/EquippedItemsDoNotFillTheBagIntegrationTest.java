package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.trade.JdbcTradeTransactionRunner;
import dev.kage.server.database.trade.PostgresTradeInventoryAdapter;

/**
 * Test bằng PostgreSQL THẬT — đồ đang mặc KHÔNG chiếm ô túi (quyết định chủ dự án 2026-10-11).
 *
 * <p>Trước đó túi 20 ô tính cả món trên người: người mặc đủ bộ chỉ còn 13 ô thật, và người giữ bộ cũ
 * trong túi bị từ chối phần thưởng quest 6 món dù quest vẫn COMPLETED. Nay 20 ô là 20 món NẰM TRONG
 * túi. Hệ quả phải gác kèm: gỡ một món là đưa nó vào túi, nên gỡ khi túi đầy bị từ chối — không thì
 * gỡ-rồi-mặc-món-khác là cách nhét quá 20 món.
 *
 * <p>Đo ở hai đường đếm khác nhau của server: aggregate {@code Inventory} (rơi đồ, cường hoá, dùng
 * đồ) và câu đếm SQL của các adapter cấp đồ (ở đây qua {@code PostgresTradeInventoryAdapter}; cửa
 * hàng, thư và quest dùng đúng câu đó).
 */
@Testcontainers
class EquippedItemsDoNotFillTheBagIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int BAG_SLOTS = 20;
    private static final int ANY_LEVEL = 100;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;
    static volatile long swordTemplateId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "bagslotstester"));
        accountId = accountHolder[0].id();
        swordTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
    }

    @Test
    void aggregateShouldLeaveTwentySlotsForTheBag_whenOneItemIsWorn() {
        long characterId = createFreshCharacter("BagSlotsA");
        long worn = insertSword(characterId);
        equip(characterId, worn);
        for (int i = 0; i < BAG_SLOTS - 1; i++) {
            insertSword(characterId);
        }
        // 1 món đang mặc + 19 món trong túi = 20 dòng. Luật cũ: túi đã đầy.
        Inventory inventory = new PostgresInventoryAdapter(dataSource)
                .findByCharacter(characterId)
                .orElseThrow();

        assertThat(inventory.items()).hasSize(BAG_SLOTS);
        assertThat(inventory.add(ItemStack.newItem(swordTemplateId, 1, 0), 1))
                .as("món thứ 20 trong túi vẫn vào được")
                .isInstanceOf(Inventory.Added.class);
        assertThat(inventory.add(ItemStack.newItem(swordTemplateId, 1, 0), 1))
                .as("món thứ 21 thì không")
                .isNotInstanceOf(Inventory.Added.class);
    }

    @Test
    void grantAdaptersShouldCountOnlyWhatIsInTheBag() {
        long characterId = createFreshCharacter("BagSlotsB");
        long worn = insertSword(characterId);
        equip(characterId, worn);
        for (int i = 0; i < BAG_SLOTS; i++) {
            insertSword(characterId);
        }
        // 1 đang mặc + 20 trong túi: đúng đầy, CHƯA vượt.
        assertThat(exceedsCapacity(characterId)).isFalse();

        insertSword(characterId);

        assertThat(exceedsCapacity(characterId)).as("21 món trong túi").isTrue();
    }

    @Test
    void unequipShouldBeRefused_whileTheBagIsFull_andWorkOnceASlotIsFree() {
        long characterId = createFreshCharacter("BagSlotsC");
        long worn = insertSword(characterId);
        equip(characterId, worn);
        long spare = 0;
        for (int i = 0; i < BAG_SLOTS; i++) {
            spare = insertSword(characterId);
        }
        UnequipItemUseCase unequip = new UnequipItemUseCase(
                        new PostgresEquipmentAdapter(dataSource), new JdbcInventoryTransactionRunner(dataSource))
                .guardedBy(new PostgresInventoryAdapter(dataSource));

        assertThat(unequip.execute(characterId, EquipmentSlotType.WEAPON))
                .isEqualTo(UnequipItemUseCase.UnequipItemResult.INVENTORY_FULL);
        assertThat(queryLong(
                        "SELECT count(*) FROM equipment_slot WHERE character_id = ? AND inventory_item_id = ?",
                        characterId,
                        worn))
                .as("món vẫn đang mặc")
                .isEqualTo(1);

        update("DELETE FROM inventory_item WHERE id = ?", spare);

        assertThat(unequip.execute(characterId, EquipmentSlotType.WEAPON))
                .isEqualTo(UnequipItemUseCase.UnequipItemResult.OK);
    }

    private static void equip(long characterId, long itemId) {
        EquipItemUseCase equip = new EquipItemUseCase(
                new PostgresInventoryAdapter(dataSource),
                new PostgresItemTemplateAdapter(dataSource),
                new PostgresEquipmentAdapter(dataSource),
                new JdbcInventoryTransactionRunner(dataSource));
        assertThat(equip.execute(characterId, itemId, EquipmentSlotType.WEAPON, ANY_LEVEL))
                .isEqualTo(EquipItemUseCase.EquipItemResult.OK);
    }

    private static boolean exceedsCapacity(long characterId) {
        AtomicBoolean exceeds = new AtomicBoolean();
        new JdbcTradeTransactionRunner(dataSource)
                .inTransaction(tx ->
                        exceeds.set(new PostgresTradeInventoryAdapter(dataSource).exceedsCapacity(tx, characterId)));
        return exceeds.get();
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

    private static long insertSword(long characterId) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, 1, 0, 0) RETURNING id
                """, characterId, swordTemplateId);
    }

    private static void update(String sql, long param) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, param);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only ghi: " + sql, e);
        }
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("không có dòng nào: %s", sql).isTrue();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only truy vấn: " + sql, e);
        }
    }
}
