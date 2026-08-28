package dev.kage.server.database.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "shoptester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = findItemTemplateIdByCode("kunai");
        woodenSwordTemplateId = findItemTemplateIdByCode("wooden_sword");
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

    private static int countInventoryRows(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm inventory_item: characterId=" + characterId, e);
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

    /** Điền túi đồ tới đầy (DEFAULT_CAPACITY=20) — dùng để test nhánh {@code INVENTORY_FULL}. */
    private static void fillInventoryToCapacity(long characterId) {
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        ArrayList<dev.kage.inventory.domain.ItemStack> stacks = new ArrayList<dev.kage.inventory.domain.ItemStack>();
        for (int i = 0; i < 20; i++) {
            stacks.add(dev.kage.inventory.domain.ItemStack.newItem(kunaiTemplateId, 1, 0));
        }
        dev.kage.inventory.domain.Inventory inventory =
                new dev.kage.inventory.domain.Inventory(characterId, 20, List.copyOf(stacks));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
    }

    private static long persistOneItem(long characterId, long itemTemplateId, int quantity) {
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        dev.kage.inventory.domain.Inventory inventory = new dev.kage.inventory.domain.Inventory(
                characterId, 20, List.of(dev.kage.inventory.domain.ItemStack.newItem(itemTemplateId, quantity, 0)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
        return inventoryAdapter
                .findByCharacter(characterId)
                .orElseThrow()
                .items()
                .get(0)
                .id();
    }

    private static ShopPorts newPorts() {
        return new ShopPorts(
                new PostgresShopCatalogAdapter(dataSource),
                new PostgresShopGoldAdapter(),
                new PostgresShopInventoryGatewayAdapter(dataSource),
                new PostgresShopAuditAdapter(),
                new PostgresShopIdempotencyAdapter());
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    private static BuyItemUseCase newBuyUseCase() {
        return new BuyItemUseCase(newPorts(), new JdbcShopTransactionRunner(dataSource));
    }

    private static SellItemUseCase newSellUseCase() {
        return new SellItemUseCase(newPorts(), new JdbcShopTransactionRunner(dataSource));
    }

    // ===== ADR-035 Decision-3 — GoldLedger là ĐIỂM PHÁT DUY NHẤT của GoldChangedEvent =====
    // Ba test dưới đây dùng CHÍNH 3 kịch bản đã có ở trên (mua được / thiếu tiền / túi đầy rồi
    // rollback), chỉ soi thêm một câu hỏi: bên nghe có được báo ĐÚNG số lần, ĐÚNG số dư không.
    // Rủi ro lớn nhất ADR nêu ra là "bỏ sót một đường đổi vàng"; rủi ro thứ hai, ít ai nghĩ tới, là
    // báo cho một số dư ĐÃ BỊ ROLLBACK — cả hai đều im lặng nếu không có test.

    // ArrayList thường, KHÔNG cần đồng bộ hoá: GoldLedger phát trên CHÍNH thread vừa chạy
    // transaction (ngay sau commit), mà ở test này đó là thread JUnit — một luồng ghi duy nhất.
    private final List<dev.kage.server.database.GoldChange> recordedGoldChanges =
            new ArrayList<dev.kage.server.database.GoldChange>();

    @org.junit.jupiter.api.BeforeEach
    void recordGoldChanges() {
        recordedGoldChanges.clear();
        dev.kage.server.database.GoldLedger.configureListener(recordedGoldChanges::add);
    }

    @org.junit.jupiter.api.AfterEach
    void stopRecordingGoldChanges() {
        // Listener là state TĨNH (cùng khuôn DbMetricsPort) — không gỡ ra thì test kế thừa lại nó.
        dev.kage.server.database.GoldLedger.configureListener(dev.kage.server.database.GoldChangeListener.NO_OP);
    }

    @Test
    void shouldPublishOneGoldChange_whenBuyCommits() {
        long characterId = createFreshCharacter("GoldEventBuy");
        grantGold(characterId, 100);

        newBuyUseCase().execute(characterId, kunaiTemplateId, 2, newIdempotencyKey()); // 2*5 = 10 gold

        assertThat(recordedGoldChanges).hasSize(1);
        dev.kage.server.database.GoldChange change = recordedGoldChanges.get(0);
        assertThat(change.characterId()).isEqualTo(characterId);
        assertThat(change.gold()).isEqualTo(90L); // SỐ DƯ TUYỆT ĐỐI sau thay đổi, không phải delta
        assertThat(change.delta()).isEqualTo(-10L);
        assertThat(change.reasonKey()).isEqualTo("gold.reason.buy_item"); // khoá i18n, không phải câu tiếng Việt
    }

    @Test
    void shouldPublishNothing_whenBuyFailsForInsufficientGold() {
        long characterId = createFreshCharacter("GoldEventPoor");
        grantGold(characterId, 1);

        newBuyUseCase().execute(characterId, kunaiTemplateId, 2, newIdempotencyKey());

        // "Không đủ tiền" KHÔNG phải một lần vàng đổi — UPDATE có điều kiện không khớp dòng nào.
        assertThat(recordedGoldChanges).isEmpty();
    }

    @Test
    void shouldPublishNothing_whenTransactionRollsBackAfterDeductingGold() {
        long characterId = createFreshCharacter("GoldEvtRollbk");
        fillInventoryToCapacity(characterId);
        grantGold(characterId, 1_000_000);

        newBuyUseCase().execute(characterId, kunaiTemplateId, 1, newIdempotencyKey());

        // Gold ĐÃ bị trừ bên trong transaction rồi mới abort vì túi đầy. Báo ở thời điểm UPDATE sẽ
        // gửi cho client một số dư KHÔNG BAO GIỜ tồn tại — đây là lý do phải chờ commit mới phát.
        assertThat(recordedGoldChanges).isEmpty();
        assertThat(readGold(characterId)).isEqualTo(1_000_000);
    }

    @Test
    void shouldPublishGoldChange_whenSellCredits() {
        long characterId = createFreshCharacter("GoldEventSell");
        long itemId = persistOneItem(characterId, kunaiTemplateId, 3);

        newSellUseCase().execute(characterId, itemId, 3, newIdempotencyKey()); // 3 * 2 = 6 gold

        assertThat(recordedGoldChanges).hasSize(1);
        // Chiều CỘNG cũng phải phát — bán xong mà số vàng trên màn hình không nhúc nhích là bug im lặng.
        assertThat(recordedGoldChanges.get(0).delta()).isEqualTo(6L);
        assertThat(recordedGoldChanges.get(0).gold()).isEqualTo(6L);
        assertThat(recordedGoldChanges.get(0).reasonKey()).isEqualTo("gold.reason.sell_item");
    }

    @Test
    void shouldDeductGoldAndGrantItem_whenBuyWithEnoughGold() {
        long characterId = createFreshCharacter("BuyRich");
        grantGold(characterId, 100);

        BuyItemUseCase.BuyResult result =
                newBuyUseCase().execute(characterId, kunaiTemplateId, 2, newIdempotencyKey()); // 2*5=10 gold

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.OK);
        assertThat(readGold(characterId)).isEqualTo(90);
        assertThat(countInventoryRows(characterId)).isEqualTo(1);
    }

    @Test
    void shouldRejectBuy_whenNotEnoughGold() {
        long characterId = createFreshCharacter("BuyPoor");
        grantGold(characterId, 1);

        BuyItemUseCase.BuyResult result =
                newBuyUseCase().execute(characterId, kunaiTemplateId, 2, newIdempotencyKey()); // cần 10 gold

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INSUFFICIENT_GOLD);
        assertThat(readGold(characterId)).isEqualTo(1); // không đổi
        assertThat(countInventoryRows(characterId)).isZero();
    }

    @Test
    void shouldRejectBuy_whenItemNotInShop() {
        long characterId = createFreshCharacter("BuyUnknown");
        grantGold(characterId, 1_000_000);

        BuyItemUseCase.BuyResult result = newBuyUseCase().execute(characterId, 999_999L, 1, newIdempotencyKey());

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.ITEM_NOT_IN_SHOP);
        assertThat(readGold(characterId)).isEqualTo(1_000_000); // không đổi
    }

    @Test
    void shouldRollbackGold_whenBuyAndInventoryFull() {
        long characterId = createFreshCharacter("BuyFullBag");
        fillInventoryToCapacity(characterId);
        grantGold(characterId, 1_000_000);

        BuyItemUseCase.BuyResult result = newBuyUseCase().execute(characterId, kunaiTemplateId, 1, newIdempotencyKey());

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

        SellItemUseCase.SellResult result =
                newSellUseCase().execute(characterId, itemId, 3, newIdempotencyKey()); // 3*2=6 gold

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.OK);
        assertThat(readGold(characterId)).isEqualTo(6);
        assertThat(countInventoryRows(characterId)).isZero(); // bán hết -> xoá dòng
    }

    @Test
    void shouldRejectSell_whenInsufficientQuantity() {
        long characterId = createFreshCharacter("SellShort");
        long itemId = persistOneItem(characterId, kunaiTemplateId, 1);
        grantGold(characterId, 0);

        SellItemUseCase.SellResult result =
                newSellUseCase().execute(characterId, itemId, 5, newIdempotencyKey()); // chỉ có 1

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

        SellItemUseCase.SellResult result = newSellUseCase().execute(characterId, itemId, 1, newIdempotencyKey());

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.ITEM_NOT_SELLABLE);
        assertThat(countInventoryRows(characterId)).isEqualTo(1); // không đổi
    }

    @Test
    void shouldRejectDuplicateBuy_whenIdempotencyKeyReused() {
        long characterId = createFreshCharacter("BuyDupe");
        grantGold(characterId, 100);
        String key = newIdempotencyKey();
        newBuyUseCase().execute(characterId, kunaiTemplateId, 1, key);

        BuyItemUseCase.BuyResult result = newBuyUseCase().execute(characterId, kunaiTemplateId, 1, key);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.DUPLICATE_REQUEST);
        assertThat(readGold(characterId)).isEqualTo(95); // chỉ trừ 1 lần (5 gold), KHÔNG lặp lại
        assertThat(countInventoryRows(characterId)).isEqualTo(1); // KHÔNG cấp item lần 2
    }

    @Test
    void shouldRejectDuplicateSell_whenIdempotencyKeyReused() {
        long characterId = createFreshCharacter("SellDupe");
        long itemId = persistOneItem(characterId, kunaiTemplateId, 5);
        grantGold(characterId, 0);
        String key = newIdempotencyKey();
        newSellUseCase().execute(characterId, itemId, 1, key);

        SellItemUseCase.SellResult result = newSellUseCase().execute(characterId, itemId, 1, key);

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.DUPLICATE_REQUEST);
        assertThat(readGold(characterId)).isEqualTo(2); // chỉ cộng 1 lần (2 gold), KHÔNG lặp lại
    }

    private static long insertUnlistedItemTemplate() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO item_template (code, name_key, max_stack) VALUES (?, ?, 99) RETURNING id")) {
            statement.setString(1, "unlisted_" + UUID.randomUUID().toString().substring(0, 8));
            statement.setString(2, "item.unlisted.name");
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only tạo item_template chưa niêm yết", e);
        }
    }
}
