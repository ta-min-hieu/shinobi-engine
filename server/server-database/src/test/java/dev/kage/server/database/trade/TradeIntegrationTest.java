package dev.kage.server.database.trade;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.CancelTradeUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import java.sql.SQLException;
import java.time.Duration;
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
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V16 ({@code trade_session}/
 * {@code trade_offer_item}) + tính NGUYÊN TỬ của {@code ConfirmTradeUseCase} (feature-trade/CLAUDE.md,
 * hệ thống nguy hiểm nhất — mọi test ở đây verify KHÔNG BAO GIỜ có duplicate item/gold).
 */
@Testcontainers
class TradeIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long kunaiTemplateId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "tradetester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = findItemTemplateIdByCode("kunai");
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

    private static long persistOneItem(long characterId, long itemTemplateId, int quantity) {
        var inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        var inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        var inventory = new dev.kage.inventory.domain.Inventory(characterId, 20,
                List.of(dev.kage.inventory.domain.ItemStack.newItem(itemTemplateId, quantity, 0)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
        return inventoryAdapter.findByCharacter(characterId).orElseThrow().items().get(0).id();
    }

    private static int itemQuantity(long itemId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT quantity FROM inventory_item WHERE id = ?")) {
            statement.setLong(1, itemId);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc quantity: itemId=" + itemId, e);
        }
    }

    private static long itemOwner(long itemId) {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement("SELECT character_id FROM inventory_item WHERE id = ?")) {
            statement.setLong(1, itemId);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc character_id: itemId=" + itemId, e);
        }
    }

    private static PostgresTradeSessionAdapter newSessionPort() {
        return new PostgresTradeSessionAdapter(dataSource);
    }

    private static ConfirmTradeUseCase newConfirmUseCase(PostgresTradeSessionAdapter sessionPort) {
        var ports = new TradePorts(
                sessionPort, new PostgresTradeInventoryAdapter(dataSource), new PostgresTradeGoldAdapter(),
                new PostgresTradeAuditAdapter(), new PostgresTradeIdempotencyAdapter());
        return new ConfirmTradeUseCase(ports, new JdbcTradeTransactionRunner(dataSource));
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void shouldExecuteTrade_swappingGoldAndItems_whenBothConfirm() {
        long a = createFreshCharacter("TradeA1");
        long b = createFreshCharacter("TradeB1");
        grantGold(a, 100);
        grantGold(b, 50);
        long itemA = persistOneItem(a, kunaiTemplateId, 3);
        long itemB = persistOneItem(b, kunaiTemplateId, 1);
        var sessionPort = newSessionPort();
        var session = new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 20, List.of(new OfferedItemInput(itemA, 3)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 10, List.of(new OfferedItemInput(itemB, 1)));
        var confirmUseCase = newConfirmUseCase(sessionPort);

        confirmUseCase.execute(session.id(), a, newIdempotencyKey());
        var result = confirmUseCase.execute(session.id(), b, newIdempotencyKey());

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.EXECUTED);
        assertThat(readGold(a)).isEqualTo(90); // 100 - 20 + 10
        assertThat(readGold(b)).isEqualTo(60); // 50 - 10 + 20
        assertThat(itemOwner(itemA)).isEqualTo(b);
        assertThat(itemOwner(itemB)).isEqualTo(a);
        assertThat(sessionPort.findActiveSessionOfCharacter(a)).isEmpty();
    }

    @Test
    void shouldSplitStack_whenOfferedQuantityLessThanRowQuantity() {
        long a = createFreshCharacter("TradeA2");
        long b = createFreshCharacter("TradeB2");
        long itemA = persistOneItem(a, kunaiTemplateId, 10); // có 10, chào 3
        var sessionPort = newSessionPort();
        var session = new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 0, List.of(new OfferedItemInput(itemA, 3)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 0, List.of());
        var confirmUseCase = newConfirmUseCase(sessionPort);

        confirmUseCase.execute(session.id(), a, newIdempotencyKey());
        confirmUseCase.execute(session.id(), b, newIdempotencyKey());

        assertThat(itemQuantity(itemA)).isEqualTo(7); // giữ lại 7
        assertThat(itemOwner(itemA)).isEqualTo(a);
    }

    @Test
    void shouldReturnOfferInvalid_andRollbackGoldToo_whenItemSoldAwayBeforeExecution() {
        long a = createFreshCharacter("TradeA3");
        long b = createFreshCharacter("TradeB3");
        grantGold(a, 100);
        long itemA = persistOneItem(a, kunaiTemplateId, 1);
        var sessionPort = newSessionPort();
        var session = new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 20, List.of(new OfferedItemInput(itemA, 1)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 0, List.of());
        newConfirmUseCase(sessionPort).execute(session.id(), b, newIdempotencyKey()); // b confirm trước
        // item đã "bán" (đổi chủ) TRƯỚC KHI a confirm — mô phỏng bán ở shop giữa chừng.
        var inventoryPort = new PostgresTradeInventoryAdapter(dataSource);
        var tradeTx = new JdbcTradeTransactionRunner(dataSource);
        tradeTx.inTransaction(tx -> inventoryPort.transferItem(
                tx, new dev.kage.trade.domain.TradeInventoryPort.TransferRequest(a, b, itemA, 1)));

        var result = newConfirmUseCase(sessionPort).execute(session.id(), a, newIdempotencyKey());

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.OFFER_INVALID);
        // KHÔNG được "trừ gold nhưng không có gì đổi lại" — Postgres thật rollback CẢ gold.
        assertThat(readGold(a)).isEqualTo(100);
        assertThat(sessionPort.findActiveSessionOfCharacter(a)).isPresent(); // phiên vẫn còn, chưa xoá
    }

    @Test
    void shouldRejectDuplicateConfirm_whenIdempotencyKeyReused() {
        long a = createFreshCharacter("TradeA4");
        long b = createFreshCharacter("TradeB4");
        grantGold(a, 100);
        grantGold(b, 100);
        var sessionPort = newSessionPort();
        var session = new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 10, List.of());
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 10, List.of());
        var confirmUseCase = newConfirmUseCase(sessionPort);
        var key = newIdempotencyKey();
        confirmUseCase.execute(session.id(), a, key);
        confirmUseCase.execute(session.id(), b, newIdempotencyKey());
        assertThat(sessionPort.findActiveSessionOfCharacter(a)).isEmpty(); // đã thi hành xong

        var result = confirmUseCase.execute(999, a, key); // retry với idempotencyKey CŨ (session khác đã bị xoá)

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.DUPLICATE_REQUEST);
    }

    @Test
    void shouldCancelTrade_beforeExecution() {
        long a = createFreshCharacter("TradeA5");
        long b = createFreshCharacter("TradeB5");
        var sessionPort = newSessionPort();
        var session = new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);

        var result = new CancelTradeUseCase(sessionPort).execute(session.id(), a);

        assertThat(result).isEqualTo(CancelTradeUseCase.Result.OK);
        assertThat(sessionPort.findActiveSessionOfCharacter(a)).isEmpty();
        assertThat(sessionPort.findActiveSessionOfCharacter(b)).isEmpty();
    }
}
