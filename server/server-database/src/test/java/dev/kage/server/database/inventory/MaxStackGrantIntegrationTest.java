package dev.kage.server.database.inventory;

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
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.shop.JdbcShopTransactionRunner;
import dev.kage.server.database.shop.PostgresShopAuditAdapter;
import dev.kage.server.database.shop.PostgresShopCatalogAdapter;
import dev.kage.server.database.shop.PostgresShopGoldAdapter;
import dev.kage.server.database.shop.PostgresShopIdempotencyAdapter;
import dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter;
import dev.kage.shop.application.BuyItemUseCase;
import dev.kage.shop.application.ShopPorts;

/**
 * {@code item_template.max_stack} phải đúng ở MỌI đường mở ô mới, không chỉ ở đường gộp chồng.
 *
 * <p>Lỗi thật (2026-09-24): khi không gộp được, shop/thư/quest {@code INSERT} MỘT dòng mang nguyên
 * số lượng yêu cầu. Client hợp lệ có sẵn ô nhập số lượng ở cửa hàng, nên gõ "50" khi mua kiếm gỗ
 * ({@code max_stack = 1}) là được một dòng ×50 — và cường hoá giữ nguyên {@code quantity} của dòng,
 * tức cường hoá 50 thanh với giá một lần. Phi đao thì một ô chứa quá 99.
 *
 * <p>Đo GIÁ TRỊ CUỐI: số lượng từng dòng trong túi sau khi cấp.
 */
@Testcontainers
class MaxStackGrantIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "maxstacktester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = templateIdOf("kunai");
        woodenSwordTemplateId = templateIdOf("wooden_sword");
    }

    @Test
    void shouldGiveOneRowPerSword_whenBuyingSeveralNonStackableItems() {
        long characterId = createFreshCharacter("MsBuySwords");
        setGold(characterId, 10_000L);

        BuyItemUseCase.BuyResult result = newBuyUseCase().execute(characterId, woodenSwordTemplateId, 3, newKey());

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.OK);
        assertThat(rowQuantities(characterId, woodenSwordTemplateId)).containsExactly(1, 1, 1);
    }

    @Test
    void shouldSplitAtMaxStack_whenBuyingMoreThanOneStack() {
        long characterId = createFreshCharacter("MsBuyKunai");
        setGold(characterId, 10_000L);

        BuyItemUseCase.BuyResult result = newBuyUseCase().execute(characterId, kunaiTemplateId, 150, newKey());

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.OK);
        assertThat(rowQuantities(characterId, kunaiTemplateId)).containsExactly(99, 51);
    }

    @Test
    void shouldRefuseAndChargeNothing_whenTheSplitNeedsMoreSlotsThanTheBagHas() {
        long characterId = createFreshCharacter("MsBuyTooMany");
        setGold(characterId, 10_000L);

        BuyItemUseCase.BuyResult result = newBuyUseCase().execute(characterId, woodenSwordTemplateId, 21, newKey());

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INVENTORY_FULL);
        assertThat(rowQuantities(characterId, woodenSwordTemplateId)).isEmpty();
        assertThat(readGold(characterId)).isEqualTo(10_000L);
    }

    /**
     * Số lượng sát {@code Integer.MAX_VALUE} (rà kinh tế 2026-10-03; client gửi {@code int32} tuỳ ý,
     * chỉ cần đủ vàng). Phép chia ô {@code (quantity + maxStack - 1) / maxStack} tràn int thành số
     * ÂM ⇒ "cần −21 ô, vừa" ⇒ không chèn dòng nào mà vẫn báo thành công: TRỪ ~10,7 tỉ vàng, KHÔNG
     * cấp gì.
     */
    @Test
    void shouldRefuseAndChargeNothing_whenQuantityOverflowsTheSlotArithmetic() {
        long characterId = createFreshCharacter("MsBuyOverflow");
        setGold(characterId, Long.MAX_VALUE / 2);

        BuyItemUseCase.BuyResult result =
                newBuyUseCase().execute(characterId, kunaiTemplateId, Integer.MAX_VALUE, newKey());

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INVENTORY_FULL);
        assertThat(rowQuantities(characterId, kunaiTemplateId)).isEmpty();
        assertThat(readGold(characterId)).isEqualTo(Long.MAX_VALUE / 2);
    }

    /**
     * Cùng số lượng, nhưng đã có sẵn một chồng phi đao: câu gộp chồng tính {@code quantity + ?}
     * bằng {@code integer} của Postgres và nổ "integer out of range" ⇒ 500 thay vì túi đầy.
     */
    @Test
    void shouldRefuseAndChargeNothing_whenQuantityWouldOverflowAnExistingStack() {
        long characterId = createFreshCharacter("MsOvfStack");
        setGold(characterId, Long.MAX_VALUE / 2);
        newBuyUseCase().execute(characterId, kunaiTemplateId, 1, newKey());
        long goldAfterFirstBuy = readGold(characterId);

        BuyItemUseCase.BuyResult result =
                newBuyUseCase().execute(characterId, kunaiTemplateId, Integer.MAX_VALUE, newKey());

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INVENTORY_FULL);
        assertThat(rowQuantities(characterId, kunaiTemplateId)).containsExactly(1);
        assertThat(readGold(characterId)).isEqualTo(goldAfterFirstBuy);
    }

    @Test
    void shouldGiveOneRowPerSword_whenMailDeliversSeveralNonStackableItems() {
        long characterId = createFreshCharacter("MsMailSwords");
        PostgresMailInventoryGatewayAdapter adapter = new PostgresMailInventoryGatewayAdapter();
        boolean[] granted = new boolean[1];

        new JdbcMailTransactionRunner(dataSource)
                .inTransaction(tx -> granted[0] = adapter.grantItem(tx, characterId, woodenSwordTemplateId, 3));

        assertThat(granted[0]).isTrue();
        assertThat(rowQuantities(characterId, woodenSwordTemplateId)).containsExactly(1, 1, 1);
    }

    @Test
    void shouldSplitAtMaxStack_whenQuestRewardExceedsOneStack() {
        long characterId = createFreshCharacter("MsQuestKunai");
        PostgresQuestInventoryGrantAdapter adapter = new PostgresQuestInventoryGrantAdapter();
        boolean[] granted = new boolean[1];

        new JdbcQuestTransactionRunner(dataSource)
                .inTransaction(tx -> granted[0] = adapter.grantItemByCode(tx, characterId, "kunai", 120));

        assertThat(granted[0]).isTrue();
        assertThat(rowQuantities(characterId, kunaiTemplateId)).containsExactly(99, 21);
    }

    private static BuyItemUseCase newBuyUseCase() {
        ShopPorts ports = new ShopPorts(
                new PostgresShopCatalogAdapter(dataSource),
                new PostgresShopGoldAdapter(),
                new PostgresShopInventoryGatewayAdapter(dataSource),
                new PostgresShopAuditAdapter(),
                new PostgresShopIdempotencyAdapter());
        return new BuyItemUseCase(ports, new JdbcShopTransactionRunner(dataSource));
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
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

    private static void setGold(long characterId, long amount) {
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

    /** Số lượng từng dòng của một loại, lớn trước — thứ tự dòng trong túi không phải điều đang đo. */
    private static List<Integer> rowQuantities(long characterId, long itemTemplateId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT quantity FROM inventory_item WHERE character_id = ? AND item_template_id = ?"
                                + " ORDER BY quantity DESC, id")) {
            statement.setLong(1, characterId);
            statement.setLong(2, itemTemplateId);
            List<Integer> quantities = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    quantities.add(rs.getInt(1));
                }
            }
            return quantities;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc inventory_item: characterId=" + characterId, e);
        }
    }

    private static long templateIdOf(String code) {
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
}
