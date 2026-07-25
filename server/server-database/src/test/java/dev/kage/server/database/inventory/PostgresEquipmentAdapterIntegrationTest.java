package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V5 (`equipment_slot`,
 * `item_template.equip_slot`) + adapter (TESTING.md §2), bao gồm seed data ('wooden_sword').
 */
@Testcontainers
class PostgresEquipmentAdapterIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "equiptester"));
        accountId = accountHolder[0].id();
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    @Test
    void shouldReturnEmptyEquipment_whenNeverEquippedAnything() {
        long characterId = createFreshCharacter("EquipEmpty");
        PostgresEquipmentAdapter adapter = new PostgresEquipmentAdapter(dataSource);

        Equipment equipment = adapter.findByCharacter(characterId).orElseThrow();

        assertThat(equipment.slots()).isEmpty();
    }

    @Test
    void shouldFindSeededWoodenSword_asWeaponEquippable() {
        PostgresItemTemplateAdapter itemTemplateAdapter = new PostgresItemTemplateAdapter(dataSource);
        long woodenSwordId = findItemTemplateIdByCode("wooden_sword"); // seed V5

        ItemTemplate template = itemTemplateAdapter.findById(woodenSwordId).orElseThrow();

        assertThat(template.equipSlot()).isEqualTo(EquipmentSlotType.WEAPON);
        assertThat(template.isEquippable()).isTrue();
    }

    @Test
    void shouldPersistEquippedItem_acrossSaveAndLoad() {
        long characterId = createFreshCharacter("EquipPersist");
        long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
        long realItemId = persistOneItem(characterId, woodenSwordId);

        PostgresEquipmentAdapter equipmentAdapter = new PostgresEquipmentAdapter(dataSource);
        JdbcInventoryTransactionRunner equipmentTx = new JdbcInventoryTransactionRunner(dataSource);
        Equipment equipment = new Equipment(characterId, Map.of(EquipmentSlotType.WEAPON, realItemId));
        equipmentTx.inTransaction(tx -> equipmentAdapter.save(tx, equipment));

        Equipment reloaded = equipmentAdapter.findByCharacter(characterId).orElseThrow();

        assertThat(reloaded.equippedItemId(EquipmentSlotType.WEAPON)).contains(realItemId);
    }

    @Test
    void shouldRemoveSlot_afterUnequipAndSave() {
        long characterId = createFreshCharacter("EquipUnequip");
        long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
        long realItemId = persistOneItem(characterId, woodenSwordId);

        PostgresEquipmentAdapter equipmentAdapter = new PostgresEquipmentAdapter(dataSource);
        JdbcInventoryTransactionRunner equipmentTx = new JdbcInventoryTransactionRunner(dataSource);
        Equipment equipped = new Equipment(characterId, Map.of(EquipmentSlotType.WEAPON, realItemId));
        equipmentTx.inTransaction(tx -> equipmentAdapter.save(tx, equipped));

        Equipment reloaded = equipmentAdapter.findByCharacter(characterId).orElseThrow();
        reloaded.unequip(EquipmentSlotType.WEAPON);
        equipmentTx.inTransaction(tx -> equipmentAdapter.save(tx, reloaded));

        Equipment afterUnequip = equipmentAdapter.findByCharacter(characterId).orElseThrow();
        assertThat(afterUnequip.slots()).isEmpty();
    }

    /** Thêm 1 item thật vào túi, trả về {@code inventory_item.id} thật (RETURNING id) để test equip. */
    private static long persistOneItem(long characterId, long itemTemplateId) {
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        Inventory inventory = new Inventory(characterId, 20, List.of(ItemStack.newItem(itemTemplateId, 1, 0)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
        return inventoryAdapter.findByCharacter(characterId).orElseThrow().items().get(0).id();
    }

    private static long findItemTemplateIdByCode(String code) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM item_template WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only lookup item_template: code=" + code, e);
        }
    }
}
