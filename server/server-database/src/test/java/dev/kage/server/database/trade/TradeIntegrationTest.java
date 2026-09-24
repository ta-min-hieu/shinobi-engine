package dev.kage.server.database.trade;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.domain.Inventory;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.CancelTradeUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "tradetester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = findItemTemplateIdByCode("kunai");
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

    private static long persistOneItem(long characterId, long itemTemplateId, int quantity) {
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        Inventory inventory = new dev.kage.inventory.domain.Inventory(
                characterId, 20, List.of(dev.kage.inventory.domain.ItemStack.newItem(itemTemplateId, quantity, 0)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
        return inventoryAdapter
                .findByCharacter(characterId)
                .orElseThrow()
                .items()
                .get(0)
                .id();
    }

    private static int itemQuantity(long itemId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT quantity FROM inventory_item WHERE id = ?")) {
            statement.setLong(1, itemId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc quantity: itemId=" + itemId, e);
        }
    }

    // Lấp túi bằng wooden_sword (max_stack = 1 — không bao giờ gộp) tới đúng {@code rows} dòng.
    private static void fillBagWithSwords(long characterId, int rows) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                        SELECT ?, id, 1, 0, ? FROM item_template WHERE code = 'wooden_sword'
                        """)) {
            for (int slot = 0; slot < rows; slot++) {
                statement.setLong(1, characterId);
                statement.setInt(2, slot);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only lấp túi: characterId=" + characterId, e);
        }
    }

    private static int bagRowCount(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm túi: characterId=" + characterId, e);
        }
    }

    private static long firstItemOf(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM inventory_item WHERE character_id = ? ORDER BY id LIMIT 1")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only tìm item: characterId=" + characterId, e);
        }
    }

    private static long itemOwner(long itemId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT character_id FROM inventory_item WHERE id = ?")) {
            statement.setLong(1, itemId);
            try (ResultSet rs = statement.executeQuery()) {
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
        TradePorts ports = new TradePorts(
                sessionPort,
                new PostgresTradeInventoryAdapter(dataSource),
                new PostgresTradeGoldAdapter(),
                new PostgresTradeAuditAdapter(),
                new PostgresTradeIdempotencyAdapter());
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
        PostgresTradeSessionAdapter sessionPort = newSessionPort();
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 20, List.of(new OfferedItemInput(itemA, 3)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 10, List.of(new OfferedItemInput(itemB, 1)));
        ConfirmTradeUseCase confirmUseCase = newConfirmUseCase(sessionPort);

        confirmUseCase.execute(session.id(), a, newIdempotencyKey());
        ConfirmTradeUseCase.Result result = confirmUseCase.execute(session.id(), b, newIdempotencyKey());

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.EXECUTED);
        assertThat(readGold(a)).isEqualTo(90); // 100 - 20 + 10
        assertThat(readGold(b)).isEqualTo(60); // 50 - 10 + 20
        assertThat(itemOwner(itemA)).isEqualTo(b);
        assertThat(itemOwner(itemB)).isEqualTo(a);
        assertThat(sessionPort.findActiveSessionOfCharacter(a)).isEmpty();
    }

    /**
     * Chuyển NGUYÊN DÒNG (đổi {@code character_id}) từng bỏ qua hẳn kiểm sức chứa — chỉ nhánh tách
     * stack mới kiểm. Túi người nhận đủ 20 dòng vẫn nhận thêm, thành 21 (sửa 2026-09-24).
     */
    @Test
    void shouldRejectTrade_whenAWholeRowWouldOverfillTheRecipientsBag() {
        long a = createFreshCharacter("TradeA7");
        long b = createFreshCharacter("TradeB7");
        long itemA = persistOneItem(a, kunaiTemplateId, 4);
        fillBagWithSwords(b, 20);
        PostgresTradeSessionAdapter sessionPort = newSessionPort();
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 0, List.of(new OfferedItemInput(itemA, 4)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 0, List.of());
        ConfirmTradeUseCase confirmUseCase = newConfirmUseCase(sessionPort);

        confirmUseCase.execute(session.id(), a, newIdempotencyKey());
        ConfirmTradeUseCase.Result result = confirmUseCase.execute(session.id(), b, newIdempotencyKey());

        assertThat(bagRowCount(b)).isEqualTo(20);
        assertThat(itemOwner(itemA)).isEqualTo(a);
        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.OFFER_INVALID);
    }

    /**
     * Đổi 1-lấy-1 giữa hai túi ĐỀU đầy là hợp lệ: giữa chừng một bên tạm có 21 dòng nhưng lúc
     * xong mỗi bên vẫn 20. Vì vậy sức chứa phải kiểm SAU mọi lượt chuyển, không phải từng lượt.
     */
    @Test
    void shouldExecuteOneForOneSwap_whenBothBagsAreFull() {
        long a = createFreshCharacter("TradeA8");
        long b = createFreshCharacter("TradeB8");
        fillBagWithSwords(a, 20);
        fillBagWithSwords(b, 20);
        long itemA = firstItemOf(a);
        long itemB = firstItemOf(b);
        PostgresTradeSessionAdapter sessionPort = newSessionPort();
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 0, List.of(new OfferedItemInput(itemA, 1)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 0, List.of(new OfferedItemInput(itemB, 1)));
        ConfirmTradeUseCase confirmUseCase = newConfirmUseCase(sessionPort);

        confirmUseCase.execute(session.id(), a, newIdempotencyKey());
        ConfirmTradeUseCase.Result result = confirmUseCase.execute(session.id(), b, newIdempotencyKey());

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.EXECUTED);
        assertThat(itemOwner(itemA)).isEqualTo(b);
        assertThat(itemOwner(itemB)).isEqualTo(a);
        assertThat(bagRowCount(a)).isEqualTo(20);
        assertThat(bagRowCount(b)).isEqualTo(20);
    }

    @Test
    void shouldSplitStack_whenOfferedQuantityLessThanRowQuantity() {
        long a = createFreshCharacter("TradeA2");
        long b = createFreshCharacter("TradeB2");
        long itemA = persistOneItem(a, kunaiTemplateId, 10); // có 10, chào 3
        PostgresTradeSessionAdapter sessionPort = newSessionPort();
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 0, List.of(new OfferedItemInput(itemA, 3)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 0, List.of());
        ConfirmTradeUseCase confirmUseCase = newConfirmUseCase(sessionPort);

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
        PostgresTradeSessionAdapter sessionPort = newSessionPort();
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 20, List.of(new OfferedItemInput(itemA, 1)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 0, List.of());
        newConfirmUseCase(sessionPort).execute(session.id(), b, newIdempotencyKey()); // b confirm trước
        // item đã "bán" (đổi chủ) TRƯỚC KHI a confirm — mô phỏng bán ở shop giữa chừng.
        PostgresTradeInventoryAdapter inventoryPort = new PostgresTradeInventoryAdapter(dataSource);
        JdbcTradeTransactionRunner tradeTx = new JdbcTradeTransactionRunner(dataSource);
        tradeTx.inTransaction(tx -> inventoryPort.transferItem(
                tx, new dev.kage.trade.domain.TradeInventoryPort.TransferRequest(a, b, itemA, 1)));

        ConfirmTradeUseCase.Result result =
                newConfirmUseCase(sessionPort).execute(session.id(), a, newIdempotencyKey());

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
        PostgresTradeSessionAdapter sessionPort = newSessionPort();
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), a, 10, List.of());
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), b, 10, List.of());
        ConfirmTradeUseCase confirmUseCase = newConfirmUseCase(sessionPort);
        String key = newIdempotencyKey();
        confirmUseCase.execute(session.id(), a, key);
        confirmUseCase.execute(session.id(), b, newIdempotencyKey());
        assertThat(sessionPort.findActiveSessionOfCharacter(a)).isEmpty(); // đã thi hành xong

        ConfirmTradeUseCase.Result result =
                confirmUseCase.execute(999, a, key); // retry với idempotencyKey CŨ (session khác đã bị xoá)

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.DUPLICATE_REQUEST);
    }

    @Test
    void shouldCancelTrade_beforeExecution() {
        long a = createFreshCharacter("TradeA5");
        long b = createFreshCharacter("TradeB5");
        PostgresTradeSessionAdapter sessionPort = newSessionPort();
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(a, b).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), b);

        CancelTradeUseCase.Result result = new CancelTradeUseCase(sessionPort).execute(session.id(), a);

        assertThat(result).isEqualTo(CancelTradeUseCase.Result.OK);
        assertThat(sessionPort.findActiveSessionOfCharacter(a)).isEmpty();
        assertThat(sessionPort.findActiveSessionOfCharacter(b)).isEmpty();
    }
}
