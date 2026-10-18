package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.character.domain.Character;
import dev.kage.market.api.MarketFacade;
import dev.kage.market.domain.ListingRequest;
import dev.kage.market.domain.MarketListing;
import dev.kage.market.domain.MarketOutcome;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;

/**
 * Phường thị (ADR-063) qua đúng composition root, trên PostgreSQL THẬT: treo → mua dời đồ + vàng và đốt 5%
 * phí; món đã cường hoá không treo được; hết hạn ⇒ đúng MỘT thư trả món dù tác vụ chạy hai lượt, và dòng
 * hết hạn không mua được nữa.
 */
@Testcontainers
@Timeout(240)
class MarketDatabaseTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "market-redis-password";

    private static final String KUNAI_ID = "SELECT id FROM item_template WHERE code = 'kunai'";
    private static final String SWORD_ID = "SELECT id FROM item_template WHERE code = 'wooden_sword'";
    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, ?, 0) RETURNING id
            """;
    private static final String SET_GOLD = "UPDATE character_stat SET gold = ? WHERE character_id = ?";
    private static final String GOLD = "SELECT gold FROM character_stat WHERE character_id = ?";
    private static final String BAG_QUANTITY = """
            SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?
            """;
    private static final String BAG_ROWS = "SELECT COUNT(*) FROM inventory_item WHERE character_id = ?";
    private static final String ROW_EXISTS = "SELECT COUNT(*) FROM inventory_item WHERE id = ?";
    private static final String EXPIRE_NOW =
            "UPDATE market_listing SET expires_at = now() - interval '1 minute' WHERE id = ?";
    private static final String UNMARK_RETURNED = "UPDATE market_listing SET returned = FALSE WHERE id = ?";
    private static final String RETURN_MAILS = """
            SELECT COUNT(*) FROM mail m JOIN mail_item mi ON mi.mail_id = m.id
             WHERE m.recipient_id = ? AND m.sender_id IS NULL AND mi.item_template_id = ? AND mi.quantity = 3
            """;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static final AtomicInteger NEXT = new AtomicInteger();
    private static GatewayFacades facades;
    private static MarketFacade market;
    private static MarketExpiryJanitor janitor;
    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    private static volatile long kunai;

    @BeforeAll
    static void wire() {
        facades = GatewayBootstrap.wireFacades(new ServerDatabaseBootstrap(
                new DatabaseConfig(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(POSTGRES_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD),
                DbMetricsPort.NO_OP));
        market = facades.progression().marketFacade();
        ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(
                facades.inventoryFacade(),
                facades.contentFacades().questFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        janitor = new MarketExpiryJanitor(
                market, facades.contentFacades().socialFacades().mailFacade(), registry);
        kunai = query(KUNAI_ID);
    }

    @Test
    void listThenBuy_shouldMoveTheItemAndGold_andBurnTheFee() {
        Character seller = newCharacter();
        Character buyer = newCharacter();
        long row = query(INSERT_ITEM, seller.id(), kunai, 5, 0);
        update(SET_GOLD, 1_000L, buyer.id());

        MarketOutcome listed = market.create(new ListingRequest(seller.id(), row, 5, 200, key()));
        MarketListing listing = market.mine(seller.id()).get(0);
        MarketOutcome bought = market.buy(buyer.id(), listing.id(), key()).outcome();

        assertThat(listed).isEqualTo(MarketOutcome.OK);
        assertThat(bought).isEqualTo(MarketOutcome.OK);
        assertThat(query(GOLD, buyer.id())).isEqualTo(800L);
        assertThat(query(GOLD, seller.id())).as("200 − phí 5% (10)").isEqualTo(190L);
        assertThat(query(BAG_QUANTITY, buyer.id(), kunai)).isEqualTo(5L);
        assertThat(query(BAG_ROWS, seller.id())).isZero();
        assertThat(market.buy(buyer.id(), listing.id(), key()).outcome()).isEqualTo(MarketOutcome.NOT_FOUND);
    }

    @Test
    void enhancedItemsShouldBeRefused_andStayInTheBag() {
        Character seller = newCharacter();
        long row = query(INSERT_ITEM, seller.id(), query(SWORD_ID), 1, 3);

        MarketOutcome outcome = market.create(new ListingRequest(seller.id(), row, 1, 500, key()));

        assertThat(outcome).isEqualTo(MarketOutcome.ENHANCED_NOT_ALLOWED);
        assertThat(query(ROW_EXISTS, row)).isEqualTo(1L);
    }

    @Test
    void expiredListingShouldBeMailedBackExactlyOnce_andNoLongerBuyable() {
        Character seller = newCharacter();
        Character buyer = newCharacter();
        update(SET_GOLD, 1_000L, buyer.id());
        long row = query(INSERT_ITEM, seller.id(), kunai, 3, 0);
        market.create(new ListingRequest(seller.id(), row, 3, 100, key()));
        MarketListing listing = market.mine(seller.id()).get(0);
        update(EXPIRE_NOW, listing.id());

        janitor.runOnce();
        update(UNMARK_RETURNED, listing.id()); // giả lập chết sau thư, trước dấu "đã trả"
        janitor.runOnce();

        assertThat(query(RETURN_MAILS, seller.id(), kunai)).isEqualTo(1L);
        assertThat(market.buy(buyer.id(), listing.id(), key()).outcome()).isEqualTo(MarketOutcome.NOT_FOUND);
        assertThat(market.browse(Optional.of(kunai)))
                .extracting(MarketListing::id)
                .doesNotContain(listing.id());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static Character newCharacter() {
        int n = NEXT.incrementAndGet();
        long accountId = facades.accountFacade().login("market" + n).accountId();
        return facades.characterFacade()
                .createCharacter(accountId, "Market" + n, UUID.randomUUID().toString());
    }

    private static long query(String sql, Object... params) {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static void update(String sql, Object... params) {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
