package dev.kage.server.database.shop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.shop.application.BuyItemUseCase;
import dev.kage.shop.application.SellItemUseCase;
import dev.kage.shop.application.ShopPorts;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V8 ({@code shop_item}) + toàn bộ
 * luồng mua/bán atomically: đổi gold + đổi item trong CÙNG 1 transaction (DATABASE.md §5,
 * feature-shop/CLAUDE.md).
 */
@Testcontainers
class ShopIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long kunaiTemplateId;
    static long woodenSwordTemplateId;

    @BeforeAll
    static void migrate() {
        var config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        var accountAdapter = new PostgresAccountAdapter();
        var accountTx = new JdbcAccountTransactionRunner(dataSource);
        var accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "shoptester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = findItemTemplateIdByCode("kunai");
        woodenSwordTemplateId = findItemTemplateIdByCode("wooden_sword");
    }

    private static long createFreshCharacter(String name) {
        var characterAdapter = new PostgresCharacterAdapter(dataSource);
        var characterTx = new JdbcCharacterTransactionRunner(dataSource);
        var newCharacter = new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        var holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static void grantGold(long characterId, long amount) {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement("UPDATE character_stat SET gold = ? WHERE character_id = ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only cấp gold: characterId=" + characterId, e);
        }
    }

    private static long readGold(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement("SELECT gold FROM character_stat WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc gold: characterId=" + characterId, e);
        }
    }

    private static int countInventoryRows(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm inventory_item: characterId=" + characterId, e);
        }
    }

    private static long findItemTemplateIdByCode(String code) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT id FROM item_template WHERE code = ?")) {
            statement.setString(1, code);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only lookup item_template: code=" + code, e);
        }
    }

    /** Điền túi đồ tới đầy (DEFAULT_CAPACITY=20) — dùng để test nhánh {@code INVENTORY_FULL}. */
    private static void fillInventoryToCapacity(long characterId) {
        var inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        var inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        var stacks = new ArrayList<dev.kage.inventory.domain.ItemStack>();
        for (int i = 0; i < 20; i++) {
            stacks.add(dev.kage.inventory.domain.ItemStack.newItem(kunaiTemplateId, 1, 0));
        }
        var inventory = new dev.kage.inventory.domain.Inventory(characterId, 20, List.copyOf(stacks));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
    }

    private static long persistOneItem(long characterId, long itemTemplateId, int quantity) {
        var inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        var inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        var inventory = new dev.kage.inventory.domain.Inventory(characterId, 20,
                List.of(dev.kage.inventory.domain.ItemStack.newItem(itemTemplateId, quantity, 0)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
        return inventoryAdapter.findByCharacter(characterId).orElseThrow().items().get(0).id();
    }

    private static ShopPorts newPorts() {
        return new ShopPorts(
                new PostgresShopCatalogAdapter(dataSource),
                new PostgresShopGoldAdapter(),
                new PostgresShopInventoryGatewayAdapter(dataSource),
                new PostgresShopAuditAdapter());
    }

    private static BuyItemUseCase newBuyUseCase() {
        return new BuyItemUseCase(newPorts(), new JdbcShopTransactionRunner(dataSource));
    }

    private static SellItemUseCase newSellUseCase() {
        return new SellItemUseCase(newPorts(), new JdbcShopTransactionRunner(dataSource));
    }

    @Test
    void shouldDeductGoldAndGrantItem_whenBuyWithEnoughGold() {
        long characterId = createFreshCharacter("BuyRich");
        grantGold(characterId, 100);

        var result = newBuyUseCase().execute(characterId, kunaiTemplateId, 2); // 2 * 5 = 10 gold

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.OK);
        assertThat(readGold(characterId)).isEqualTo(90);
        assertThat(countInventoryRows(characterId)).isEqualTo(1);
    }

    @Test
    void shouldRejectBuy_whenNotEnoughGold() {
        long characterId = createFreshCharacter("BuyPoor");
        grantGold(characterId, 1);

        var result = newBuyUseCase().execute(characterId, kunaiTemplateId, 2); // cần 10 gold

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INSUFFICIENT_GOLD);
        assertThat(readGold(characterId)).isEqualTo(1); // không đổi
        assertThat(countInventoryRows(characterId)).isZero();
    }

    @Test
    void shouldRejectBuy_whenItemNotInShop() {
        long characterId = createFreshCharacter("BuyUnknown");
        grantGold(characterId, 1_000_000);

        var result = newBuyUseCase().execute(characterId, 999_999L, 1);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.ITEM_NOT_IN_SHOP);
        assertThat(readGold(characterId)).isEqualTo(1_000_000); // không đổi
    }

    @Test
    void shouldRollbackGold_whenBuyAndInventoryFull() {
        long characterId = createFreshCharacter("BuyFullBag");
        fillInventoryToCapacity(characterId);
        grantGold(characterId, 1_000_000);

        var result = newBuyUseCase().execute(characterId, kunaiTemplateId, 1);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INVENTORY_FULL);
        // KHÔNG được "trừ tiền nhưng không nhận item" (DATABASE.md §5) — rollback toàn bộ.
        assertThat(readGold(characterId)).isEqualTo(1_000_000);
        assertThat(countInventoryRows(characterId)).isEqualTo(20);
    }

    @Test
    void shouldCreditGoldAndRemoveItem_whenSellFullStack() {
        long characterId = createFreshCharacter("SellFull");
        long itemId = persistOneItem(characterId, kunaiTemplateId, 3);
        grantGold(characterId, 0);

        var result = newSellUseCase().execute(characterId, itemId, 3); // 3 * 2 = 6 gold

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.OK);
        assertThat(readGold(characterId)).isEqualTo(6);
        assertThat(countInventoryRows(characterId)).isZero(); // bán hết -> xoá dòng
    }

    @Test
    void shouldRejectSell_whenInsufficientQuantity() {
        long characterId = createFreshCharacter("SellShort");
        long itemId = persistOneItem(characterId, kunaiTemplateId, 1);
        grantGold(characterId, 0);

        var result = newSellUseCase().execute(characterId, itemId, 5); // chỉ có 1

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.INSUFFICIENT_QUANTITY);
        assertThat(readGold(characterId)).isZero();
        assertThat(countInventoryRows(characterId)).isEqualTo(1); // không đổi
    }

    @Test
    void shouldRejectSell_whenItemNotSellable() {
        // wooden_sword CÓ trong shop_item (V8 seed) -> dùng item KHÔNG có trong catalog để test
        // nhánh này: tạo 1 item_template mới không seed vào shop_item.
        long characterId = createFreshCharacter("SellUnlisted");
        long unlistedTemplateId = insertUnlistedItemTemplate();
        long itemId = persistOneItem(characterId, unlistedTemplateId, 1);

        var result = newSellUseCase().execute(characterId, itemId, 1);

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.ITEM_NOT_SELLABLE);
        assertThat(countInventoryRows(characterId)).isEqualTo(1); // không đổi
    }

    private static long insertUnlistedItemTemplate() {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO item_template (code, name_key, max_stack) VALUES (?, ?, 99) RETURNING id")) {
            statement.setString(1, "unlisted_" + UUID.randomUUID().toString().substring(0, 8));
            statement.setString(2, "item.unlisted.name");
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only tạo item_template chưa niêm yết", e);
        }
    }
}
