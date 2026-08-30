package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
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
    void shouldReadFourStatColumnsAndRequiredLevel_fromV22() {
        // ADR-036 / migration V22 — 4 cột mới phải ĐỌC được từ Postgres THẬT, không chỉ tồn tại
        // trong record Java. Chọn genin_vest vì nó là ví dụ rõ nhất của cái V22 sinh ra để sửa:
        // trước V22 nó cộng 7 ATK (một cái áo giáp làm người ta đánh mạnh hơn), nay cộng DEF/RES/HP
        // và KHÔNG cộng ATK.
        PostgresItemTemplateAdapter adapter = new PostgresItemTemplateAdapter(dataSource);
        long geninVestId = findItemTemplateIdByCode("genin_vest"); // seed V21, cân bằng lại ở V22

        ItemTemplate vest = adapter.findById(geninVestId).orElseThrow();

        assertThat(vest.equipSlot()).isEqualTo(EquipmentSlotType.ARMOR);
        assertThat(vest.bonusAtk()).isZero();
        assertThat(vest.bonusDef()).isEqualTo(10);
        assertThat(vest.bonusRes()).isEqualTo(6);
        assertThat(vest.bonusHp()).isEqualTo(20);
        assertThat(vest.requiredLevel()).isEqualTo(5);
    }

    @Test
    void shouldDefaultRequiredLevelToOne_forItemsPredatingV22() {
        // Cột thêm kèm DEFAULT nên dữ liệu CŨ không phải backfill: kunai (V4, không mặc được) vẫn
        // hợp lệ và required_level = 1, không phải 0 hay NULL.
        PostgresItemTemplateAdapter adapter = new PostgresItemTemplateAdapter(dataSource);
        long kunaiId = findItemTemplateIdByCode("kunai");

        ItemTemplate kunai = adapter.findById(kunaiId).orElseThrow();

        assertThat(kunai.isEquippable()).isFalse();
        assertThat(kunai.requiredLevel()).isEqualTo(1);
        assertThat(kunai.bonusDef()).isZero();
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
        return inventoryAdapter
                .findByCharacter(characterId)
                .orElseThrow()
                .items()
                .get(0)
                .id();
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
