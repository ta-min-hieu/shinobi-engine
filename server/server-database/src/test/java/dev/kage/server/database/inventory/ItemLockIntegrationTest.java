package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

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
import dev.kage.inventory.domain.ItemLockPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.shop.JdbcShopTransactionRunner;
import dev.kage.server.database.shop.PostgresShopAuditAdapter;
import dev.kage.server.database.shop.PostgresShopCatalogAdapter;
import dev.kage.server.database.shop.PostgresShopGoldAdapter;
import dev.kage.server.database.shop.PostgresShopIdempotencyAdapter;
import dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter;
import dev.kage.server.database.trade.JdbcTradeTransactionRunner;
import dev.kage.server.database.trade.PostgresTradeAuditAdapter;
import dev.kage.server.database.trade.PostgresTradeGoldAdapter;
import dev.kage.server.database.trade.PostgresTradeIdempotencyAdapter;
import dev.kage.server.database.trade.PostgresTradeInventoryAdapter;
import dev.kage.server.database.trade.PostgresTradeSessionAdapter;
import dev.kage.shop.application.SellItemUseCase;
import dev.kage.shop.application.ShopPorts;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;

/**
 * ADR-159 — món người chơi đã KHOÁ không rời túi bằng đường nào (bán NPC, giao dịch, thư, nguyên liệu, nộp nhiệm vụ),
 * nhưng vẫn DÙNG trực tiếp được. Mỗi ô đo giá trị cuối trong DB.
 */
@Testcontainers
final class ItemLockIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long swordTemplateId;
    static long katanaTemplateId;
    static PostgresItemLockAdapter locks;

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
        locks = new PostgresItemLockAdapter(dataSource);

        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "itemlocktester"));
        accountId = accountHolder[0].id();
        swordTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
        katanaTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "steel_katana");
    }

    @Test
    void lockedItemShouldNotBeSoldUntilUnlocked() {
        long seller = createFreshCharacter("LkSell");
        long katana = insertItem(seller, katanaTemplateId);
        assertThat(locks.setLocked(seller, katana, true)).isEqualTo(ItemLockPort.Outcome.OK);

        assertThat(sellOne(seller, katana)).isNotEqualTo(SellItemUseCase.SellResult.OK);
        assertThat(rowExists(katana)).isTrue();

        assertThat(locks.setLocked(seller, katana, false)).isEqualTo(ItemLockPort.Outcome.OK);
        assertThat(sellOne(seller, katana)).isEqualTo(SellItemUseCase.SellResult.OK);
        assertThat(rowExists(katana)).isFalse();
    }

    @Test
    void lockedItemShouldNotBeTraded() {
        long giver = createFreshCharacter("LkTrA");
        long receiver = createFreshCharacter("LkTrB");
        long sword = insertItem(giver, swordTemplateId);
        locks.setLocked(giver, sword, true);

        ConfirmTradeUseCase.Result result = executeOneSidedTrade(giver, receiver, sword);

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.OFFER_INVALID);
        assertThat(queryLong("SELECT character_id FROM inventory_item WHERE id = ?", sword))
                .isEqualTo(giver);
    }

    @Test
    void lockedStackShouldNotBeMailedOrUsedAsMaterial_butCanStillBeUsedDirectly() {
        long owner = createFreshCharacter("LkMail");
        long sword = insertItem(owner, swordTemplateId);
        locks.setLocked(owner, sword, true);
        PostgresMailInventoryGatewayAdapter mail = new PostgresMailInventoryGatewayAdapter();
        boolean[] mailed = new boolean[1];

        new JdbcMailTransactionRunner(dataSource)
                .inTransaction(tx -> mailed[0] = mail.consumeItem(tx, owner, swordTemplateId, 1));

        assertThat(mailed[0]).isFalse();
        assertThat(rowExists(sword)).isTrue();
        assertThat(inTransaction(c -> InventoryItemTake.takeUnlockedByCode(c, owner, "wooden_sword", 1)))
                .isFalse();
        assertThat(inTransaction(
                        c -> TemplateQuantityConsumer.consumeUnlocked(c, owner, new ItemLine(swordTemplateId, 1, 0))))
                .isFalse();
        assertThat(rowExists(sword)).isTrue();
        // Dùng trực tiếp (đan, phù) vẫn được: đường "use" không đọc cờ khoá.
        assertThat(inTransaction(c -> TemplateQuantityConsumer.consume(c, owner, swordTemplateId, 1)))
                .isTrue();
        assertThat(rowExists(sword)).isFalse();
    }

    @Test
    void lockingSomeoneElsesItemShouldBeNotFound_andListShouldOnlyHoldLockedItems() {
        long owner = createFreshCharacter("LkOwnA");
        long stranger = createFreshCharacter("LkOwnB");
        long locked = insertItem(owner, swordTemplateId);
        long free = insertItem(owner, katanaTemplateId);

        assertThat(locks.setLocked(stranger, locked, true)).isEqualTo(ItemLockPort.Outcome.NOT_FOUND);
        assertThat(locks.lockedItems(owner)).isEmpty();
        locks.setLocked(owner, locked, true);

        assertThat(locks.lockedItems(owner)).containsExactly(locked).doesNotContain(free);
        assertThat(queryLong("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'ITEM_LOCKED'", owner))
                .isEqualTo(1L);
    }

    private static boolean inTransaction(Predicate<Connection> work) {
        return JdbcTransactionRunner.call(dataSource, work::test);
    }

    private static SellItemUseCase.SellResult sellOne(long characterId, long itemId) {
        ShopPorts ports = new ShopPorts(
                new PostgresShopCatalogAdapter(dataSource),
                new PostgresShopGoldAdapter(),
                new PostgresShopInventoryGatewayAdapter(dataSource),
                new PostgresShopAuditAdapter(),
                new PostgresShopIdempotencyAdapter());
        return new SellItemUseCase(ports, new JdbcShopTransactionRunner(dataSource))
                .execute(characterId, itemId, 1, UUID.randomUUID().toString());
    }

    private static long insertItem(long characterId, long templateId) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, 1, 0, 0) RETURNING id
                """, characterId, templateId);
    }

    private static ConfirmTradeUseCase.Result executeOneSidedTrade(long giver, long receiver, long itemId) {
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(giver, receiver).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), receiver);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), giver, 0, List.of(new OfferedItemInput(itemId, 1)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), receiver, 0, List.of());
        TradePorts ports = new TradePorts(
                sessionPort,
                new PostgresTradeInventoryAdapter(dataSource),
                new PostgresTradeGoldAdapter(),
                new PostgresTradeAuditAdapter(),
                new PostgresTradeIdempotencyAdapter());
        ConfirmTradeUseCase confirm = new ConfirmTradeUseCase(ports, new JdbcTradeTransactionRunner(dataSource));
        confirm.execute(session.id(), giver, UUID.randomUUID().toString());
        return confirm.execute(session.id(), receiver, UUID.randomUUID().toString());
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
