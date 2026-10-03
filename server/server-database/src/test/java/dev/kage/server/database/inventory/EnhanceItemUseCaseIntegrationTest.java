package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V7 (`character_stat.gold`,
 * `audit_log`) + toàn bộ luồng {@link EnhanceItemUseCase} atomically: trừ gold + đổi enhance_level
 * + ghi audit_log trong CÙNG 1 transaction (DATABASE.md §5, GAMEPLAY.md §7).
 */
@Testcontainers
class EnhanceItemUseCaseIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "enhancetester"));
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

    private static void grantGold(long characterId, long amount) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE character_stat SET gold = ? WHERE character_id = ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only cấp gold: characterId=" + characterId, e);
        }
    }

    private static long readGold(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT gold FROM character_stat WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc gold: characterId=" + characterId, e);
        }
    }

    private static int countAuditLogRows(long characterId, String action) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = ?")) {
            statement.setLong(1, characterId);
            statement.setString(2, action);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm audit_log: characterId=" + characterId, e);
        }
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

    private static EnhanceItemUseCase newUseCase() {
        PostgresInventoryAdapter inventoryPort = new PostgresInventoryAdapter(dataSource);
        PostgresGoldAdapter goldPort = new PostgresGoldAdapter();
        PostgresAuditAdapter auditPort = new PostgresAuditAdapter();
        PostgresItemTemplateAdapter itemTemplatePort = new PostgresItemTemplateAdapter(dataSource);
        PostgresInventoryIdempotencyAdapter idempotencyPort = new PostgresInventoryIdempotencyAdapter();
        JdbcInventoryTransactionRunner tx = new JdbcInventoryTransactionRunner(dataSource);
        EnhancePorts ports = new EnhancePorts(
                inventoryPort,
                goldPort,
                auditPort,
                itemTemplatePort,
                idempotencyPort,
                new PostgresInventoryTradeOfferAdapter());
        return new EnhanceItemUseCase(ports, tx);
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    private static void grantProtectionCharm(long characterId) {
        long protectionTemplateId = findItemTemplateIdByCode(EnhancementTable.PROTECTION_CHARM_CODE);
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        Inventory existing = inventoryAdapter.findByCharacter(characterId).orElseThrow();
        ArrayList<ItemStack> items = new java.util.ArrayList<>(existing.items());
        items.add(ItemStack.newItem(protectionTemplateId, 1, 0));
        Inventory updated = new Inventory(characterId, 20, items);
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, updated));
    }

    @Test
    void shouldRejectEnhance_whenNotEnoughGold() {
        long characterId = createFreshCharacter("EnhancePoor");
        long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
        long realItemId = persistOneItem(characterId, woodenSwordId);
        grantGold(characterId, 0); // không đủ trả 100 gold cường hoá +0->+1

        EnhanceItemUseCase.EnhanceResult result =
                newUseCase().execute(characterId, realItemId, false, newIdempotencyKey());

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.INSUFFICIENT_GOLD);
        assertThat(readGold(characterId)).isZero(); // không bị trừ gì
        assertThat(countAuditLogRows(characterId, "ENHANCE_ITEM")).isZero();
    }

    @Test
    void shouldDeductGoldAndChangeLevelAndAudit_whenEnoughGold() {
        long characterId = createFreshCharacter("EnhanceRich");
        long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
        long realItemId = persistOneItem(characterId, woodenSwordId);
        grantGold(characterId, 100); // đúng giá cường hoá +0->+1

        EnhanceItemUseCase.EnhanceResult result =
                newUseCase().execute(characterId, realItemId, false, newIdempotencyKey());

        assertThat(result)
                .isIn(EnhanceItemUseCase.EnhanceResult.SUCCESS, EnhanceItemUseCase.EnhanceResult.FAILED_NO_DROP);
        assertThat(readGold(characterId)).isZero(); // đã trừ hết 100 gold dù thành công hay thất bại
        assertThat(countAuditLogRows(characterId, "ENHANCE_ITEM")).isEqualTo(1);
    }

    @Test
    void shouldRejectEnhance_whenItemAlreadyAtMaxLevel() {
        long characterId = createFreshCharacter("EnhanceMaxed");
        long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        Inventory maxedInventory =
                new Inventory(characterId, 20, List.of(ItemStack.newItem(woodenSwordId, 1, ItemStack.MAX_ENHANCE)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, maxedInventory));
        long realItemId = inventoryAdapter
                .findByCharacter(characterId)
                .orElseThrow()
                .items()
                .get(0)
                .id();
        grantGold(characterId, 1_000_000);

        EnhanceItemUseCase.EnhanceResult result =
                newUseCase().execute(characterId, realItemId, false, newIdempotencyKey());

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.MAX_LEVEL);
        assertThat(readGold(characterId)).isEqualTo(1_000_000); // không trừ gold
    }

    @Test
    void shouldConsumeProtectionAndKeepLevel_whenFailAtOrAboveThresholdAndProtectionOwned() {
        // Cấp 14 (+14->+15) có tỉ lệ thất bại 95% (EnhancementTable) — gần chắc chắn thất bại ngay,
        // vẫn lặp vài lần cho chắc (thống kê, không mock random, cùng tinh thần LootRollerTest).
        boolean sawProtected = false;
        for (int i = 0; i < 20 && !sawProtected; i++) {
            long characterId = createFreshCharacter("EnhProtect" + i);
            long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
            PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
            JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
            inventoryTx.inTransaction(tx -> inventoryAdapter.save(
                    tx, new Inventory(characterId, 20, List.of(ItemStack.newItem(woodenSwordId, 1, 14)))));
            long realItemId = inventoryAdapter
                    .findByCharacter(characterId)
                    .orElseThrow()
                    .items()
                    .get(0)
                    .id();
            grantProtectionCharm(characterId);
            grantGold(characterId, EnhancementTable.goldCost(14));

            EnhanceItemUseCase.EnhanceResult result =
                    newUseCase().execute(characterId, realItemId, true, newIdempotencyKey());

            if (result == EnhanceItemUseCase.EnhanceResult.FAILED_PROTECTED) {
                sawProtected = true;
                Optional<ItemStack> finalItem = inventoryAdapter
                        .findByCharacter(characterId)
                        .orElseThrow()
                        .findItem(realItemId);
                assertThat(finalItem)
                        .isPresent()
                        .get()
                        .extracting(ItemStack::enhanceLevel)
                        .isEqualTo(14); // KHÔNG rớt cấp
                long protectionTemplateId = findItemTemplateIdByCode(EnhancementTable.PROTECTION_CHARM_CODE);
                assertThat(inventoryAdapter
                                .findByCharacter(characterId)
                                .orElseThrow()
                                .findByTemplate(protectionTemplateId))
                        .isEmpty(); // Bảo Vệ đã tiêu
                assertThat(countAuditLogRows(characterId, "ENHANCE_ITEM")).isEqualTo(1);
            }
        }
        assertThat(sawProtected).isTrue();
    }

    @Test
    void shouldStillDropLevel_whenProtectionRequestedButNoneOwned() {
        boolean sawDropped = false;
        for (int i = 0; i < 20 && !sawDropped; i++) {
            long characterId = createFreshCharacter("EnhNoCharm" + i);
            long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
            PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
            JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
            inventoryTx.inTransaction(tx -> inventoryAdapter.save(
                    tx, new Inventory(characterId, 20, List.of(ItemStack.newItem(woodenSwordId, 1, 14)))));
            long realItemId = inventoryAdapter
                    .findByCharacter(characterId)
                    .orElseThrow()
                    .items()
                    .get(0)
                    .id();
            // KHÔNG gọi grantProtectionCharm — túi không có Bảo Vệ dù client bật cờ useProtection.
            grantGold(characterId, EnhancementTable.goldCost(14));

            EnhanceItemUseCase.EnhanceResult result =
                    newUseCase().execute(characterId, realItemId, true, newIdempotencyKey());

            if (result == EnhanceItemUseCase.EnhanceResult.FAILED_DROPPED) {
                sawDropped = true;
                Optional<ItemStack> finalItem = inventoryAdapter
                        .findByCharacter(characterId)
                        .orElseThrow()
                        .findItem(realItemId);
                assertThat(finalItem)
                        .isPresent()
                        .get()
                        .extracting(ItemStack::enhanceLevel)
                        .isEqualTo(13);
            }
        }
        assertThat(sawDropped).isTrue();
    }

    @Test
    void shouldRejectDuplicateEnhance_whenIdempotencyKeyReused() {
        long characterId = createFreshCharacter("EnhanceDupe");
        long woodenSwordId = findItemTemplateIdByCode("wooden_sword");
        long realItemId = persistOneItem(characterId, woodenSwordId);
        grantGold(characterId, 100); // đúng giá cường hoá +0->+1
        String key = newIdempotencyKey();
        newUseCase().execute(characterId, realItemId, false, key);

        EnhanceItemUseCase.EnhanceResult result = newUseCase().execute(characterId, realItemId, false, key);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.DUPLICATE_REQUEST);
        assertThat(readGold(characterId)).isZero(); // chỉ trừ 1 lần (100 gold), KHÔNG lặp lại
        assertThat(countAuditLogRows(characterId, "ENHANCE_ITEM")).isEqualTo(1); // KHÔNG ghi audit lần 2
    }
}
