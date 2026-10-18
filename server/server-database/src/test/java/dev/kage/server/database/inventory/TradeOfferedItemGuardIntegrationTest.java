package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import dev.kage.inventory.application.UseItemUseCase;
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
import dev.kage.server.database.quest.PostgresQuestItemTakeAdapter;
import dev.kage.server.database.shop.JdbcShopTransactionRunner;
import dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter;
import dev.kage.server.database.trade.PostgresTradeSessionAdapter;
import dev.kage.shop.domain.RemoveItemOutcome;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.CancelTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.domain.OfferedItemInput;

/**
 * Dòng 80 (chủ dự án duyệt 2026-10-18): phần ĐANG CHÀO trong một phiên giao dịch còn mở không rời
 * túi được qua cửa hàng, thư, {@code ctx.take_item} của quest hay dùng vật phẩm — chỉ phần còn lại.
 *
 * <p>Mỗi ô dựng một chồng 10 viên đan, chào 4 viên, rồi đo GIÁ TRỊ CUỐI: số viên còn trong túi.
 * Trước bản vá, cả bốn đường lấy luôn phần đang chào và giao dịch chỉ thất bại lúc xác nhận.
 */
@Testcontainers
class TradeOfferedItemGuardIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int STACK = 10;
    private static final int OFFERED = 4;
    private static final int FREE = STACK - OFFERED;
    private static final String PILL_CODE = "minor_healing_pill";
    private static final String INSERT_ITEM =
            "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                    + " VALUES (?, ?, ?, 0, 0) RETURNING id";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static volatile long accountId;
    static volatile long pillTemplateId;
    static final AtomicInteger NAME_COUNTER = new AtomicInteger();

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
        AtomicReference<Account> account = new AtomicReference<>();
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> account.set(accountAdapter.findOrCreate(tx, "offerguardtester")));
        accountId = account.get().id();
        pillTemplateId = queryLong("SELECT id FROM item_template WHERE code = '" + PILL_CODE + "'");
    }

    @Test
    void shouldReportOfferedQuantity_onlyWhileTheSessionIsOpen() {
        Offer offer = offerFourPills();
        AtomicInteger seen = new AtomicInteger();
        new JdbcInventoryTransactionRunner(dataSource)
                .inTransaction(tx -> seen.set(new PostgresInventoryTradeOfferAdapter()
                        .offeredQuantity(tx, offer.giverId(), offer.stackId())));
        assertThat(seen.get()).isEqualTo(OFFERED);

        new CancelTradeUseCase(offer.sessionPort()).execute(offer.sessionId(), offer.receiverId());
        new JdbcInventoryTransactionRunner(dataSource)
                .inTransaction(tx -> seen.set(new PostgresInventoryTradeOfferAdapter()
                        .offeredQuantity(tx, offer.giverId(), offer.stackId())));
        assertThat(seen.get()).as("phiên đã huỷ thì không còn gì đang chào").isZero();
    }

    @Test
    void shouldSellOnlyTheUnofferedPart() {
        Offer offer = offerFourPills();
        PostgresShopInventoryGatewayAdapter shop = new PostgresShopInventoryGatewayAdapter(dataSource);
        AtomicReference<RemoveItemOutcome> outcome = new AtomicReference<>();

        new JdbcShopTransactionRunner(dataSource)
                .inTransaction(tx ->
                        outcome.set(shop.removeUnequippedQuantity(tx, offer.giverId(), offer.stackId(), FREE + 1)));
        assertThat(outcome.get()).isEqualTo(RemoveItemOutcome.ITEM_IN_TRADE);
        assertThat(quantity(offer.stackId())).isEqualTo(STACK);

        new JdbcShopTransactionRunner(dataSource)
                .inTransaction(
                        tx -> outcome.set(shop.removeUnequippedQuantity(tx, offer.giverId(), offer.stackId(), FREE)));
        assertThat(outcome.get()).isEqualTo(RemoveItemOutcome.OK);
        assertThat(quantity(offer.stackId())).as("đúng phần đang chào còn lại").isEqualTo(OFFERED);
    }

    @Test
    void shouldMailOnlyTheUnofferedPart() {
        Offer offer = offerFourPills();
        PostgresMailInventoryGatewayAdapter mail = new PostgresMailInventoryGatewayAdapter();
        AtomicBoolean consumed = new AtomicBoolean();

        new JdbcMailTransactionRunner(dataSource)
                .inTransaction(tx -> consumed.set(mail.consumeItem(tx, offer.giverId(), pillTemplateId, FREE + 1)));
        assertThat(consumed.get()).isFalse();
        assertThat(quantity(offer.stackId())).isEqualTo(STACK);

        new JdbcMailTransactionRunner(dataSource)
                .inTransaction(tx -> consumed.set(mail.consumeItem(tx, offer.giverId(), pillTemplateId, FREE)));
        assertThat(consumed.get()).isTrue();
        assertThat(quantity(offer.stackId())).isEqualTo(OFFERED);
    }

    @Test
    void shouldLetQuestTakeOnlyTheUnofferedPart() {
        Offer offer = offerFourPills();
        PostgresQuestItemTakeAdapter quest = new PostgresQuestItemTakeAdapter();
        AtomicBoolean taken = new AtomicBoolean();

        new JdbcQuestTransactionRunner(dataSource)
                .inTransaction(tx -> taken.set(quest.takeItemByCode(tx, offer.giverId(), PILL_CODE, FREE + 1)));
        assertThat(taken.get()).isFalse();
        assertThat(quantity(offer.stackId())).isEqualTo(STACK);

        new JdbcQuestTransactionRunner(dataSource)
                .inTransaction(tx -> taken.set(quest.takeItemByCode(tx, offer.giverId(), PILL_CODE, FREE)));
        assertThat(taken.get()).isTrue();
        assertThat(quantity(offer.stackId())).isEqualTo(OFFERED);
    }

    @Test
    void shouldRefuseToUseAPill_whenEveryPillOfTheStackIsOffered() {
        long giverId = newCharacter();
        long receiverId = newCharacter();
        long stackId = givePills(giverId, OFFERED);
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        long sessionId = openSession(sessionPort, giverId, receiverId);
        new SetTradeOfferUseCase(sessionPort)
                .execute(sessionId, giverId, 0, List.of(new OfferedItemInput(stackId, OFFERED)));

        UseItemUseCase.UseItemResult result = newUseItemUseCase().execute(giverId, stackId);

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.ITEM_IN_TRADE);
        assertThat(quantity(stackId)).isEqualTo(OFFERED);
    }

    @Test
    void shouldUseAPill_fromThePartThatIsNotOffered() {
        Offer offer = offerFourPills();

        UseItemUseCase.UseItemResult result = newUseItemUseCase().execute(offer.giverId(), offer.stackId());

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.OK);
        assertThat(quantity(offer.stackId())).isEqualTo(STACK - 1);
    }

    // ---------------------------------------------------------------- dựng dữ liệu

    private record Offer(
            PostgresTradeSessionAdapter sessionPort, long sessionId, long giverId, long receiverId, long stackId) {}

    private static Offer offerFourPills() {
        long giverId = newCharacter();
        long receiverId = newCharacter();
        long stackId = givePills(giverId, STACK);
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        long sessionId = openSession(sessionPort, giverId, receiverId);
        new SetTradeOfferUseCase(sessionPort)
                .execute(sessionId, giverId, 0, List.of(new OfferedItemInput(stackId, OFFERED)));
        return new Offer(sessionPort, sessionId, giverId, receiverId, stackId);
    }

    private static UseItemUseCase newUseItemUseCase() {
        return new UseItemUseCase(
                new UseItemUseCase.Ports(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresAuditAdapter(),
                        new PostgresInventoryTradeOfferAdapter()),
                new JdbcInventoryTransactionRunner(dataSource));
    }

    private static long openSession(PostgresTradeSessionAdapter sessionPort, long giverId, long receiverId) {
        long sessionId = new RequestTradeUseCase(sessionPort)
                .execute(giverId, receiverId)
                .session()
                .id();
        new AcceptTradeRequestUseCase(sessionPort).execute(sessionId, receiverId);
        return sessionId;
    }

    private static long newCharacter() {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "OfferGuard" + NAME_COUNTER.incrementAndGet(),
                Faction.HOA,
                UUID.randomUUID().toString());
        AtomicReference<Character> holder = new AtomicReference<>();
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder.set(characterAdapter.create(tx, newCharacter)));
        return holder.get().id();
    }

    private static long givePills(long characterId, int count) {
        return queryLong(INSERT_ITEM, characterId, pillTemplateId, count);
    }

    private static long quantity(long itemId) {
        return queryLong("SELECT quantity FROM inventory_item WHERE id = ?", itemId);
    }

    private static long queryLong(String sql, long... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setLong(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }
}
